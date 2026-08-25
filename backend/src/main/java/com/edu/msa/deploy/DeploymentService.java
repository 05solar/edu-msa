package com.edu.msa.deploy;

import com.edu.msa.common.DeploymentStatus;
import com.edu.msa.common.NotiKind;
import com.edu.msa.common.ProgramStatus;
import com.edu.msa.deploy.domain.Deployment;
import com.edu.msa.deploy.domain.ServiceSpec;
import com.edu.msa.deploy.dto.DeployDtos.DeployRequest;
import com.edu.msa.deploy.dto.DeployDtos.DeploymentResponse;
import com.edu.msa.deploy.dto.DeployDtos.SpecView;
import com.edu.msa.deploy.dto.DeployDtos.ValidateRequest;
import com.edu.msa.deploy.dto.DeployDtos.ValidationResult;
import com.edu.msa.common.Role;
import com.edu.msa.deploy.repository.DeploymentRepository;
import com.edu.msa.notification.NotificationService;
import com.edu.msa.program.domain.Program;
import com.edu.msa.program.repository.ProgramRepository;
import com.edu.msa.user.repository.AppUserRepository;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** GitHub 레포 → 규격 검증 → 이미지 빌드 → K8s 매니페스트 렌더/적용 → 공개 파이프라인. */
@Service
public class DeploymentService {

    private final SourceResolver resolver;
    private final SpecParser parser;
    private final ServiceSpecValidator validator;
    private final ManifestRenderer renderer;
    private final DeploymentRepository deployments;
    private final DeployProperties props;
    private final CommandRunner runner;
    private final ProgramRepository programs;
    private final NotificationService notifications;
    private final AppUserRepository appUsers;

    public DeploymentService(SourceResolver resolver, SpecParser parser, ServiceSpecValidator validator,
                             ManifestRenderer renderer, DeploymentRepository deployments, DeployProperties props,
                             CommandRunner runner, ProgramRepository programs, NotificationService notifications,
                             AppUserRepository appUsers) {
        this.resolver = resolver;
        this.parser = parser;
        this.validator = validator;
        this.renderer = renderer;
        this.deployments = deployments;
        this.props = props;
        this.runner = runner;
        this.programs = programs;
        this.notifications = notifications;
        this.appUsers = appUsers;
    }

    /**
     * 업로더 신뢰도에 따라 배포 네임스페이스를 정한다.
     * 내부 직원(CODER/ADMIN) → edu-services, 외부 사용자(USER)·익명·불명 → edu-services-public(비신뢰).
     */
    private String resolveNamespace(Long programId) {
        if (programId == null) return props.namespacePublic();
        Program p = programs.findById(programId).orElse(null);
        if (p == null) return props.namespacePublic();
        Role role = appUsers.findByName(p.getOwner()).map(u -> u.getRole()).orElse(null);
        boolean internal = role == Role.CODER || role == Role.ADMIN;
        return internal ? props.namespace() : props.namespacePublic();
    }

    @Transactional(readOnly = true)
    public ValidationResult validate(ValidateRequest req) {
        String yaml;
        String resolvedFrom;
        boolean hasDockerfile;
        try {
            if (req.serviceYaml() != null && !req.serviceYaml().isBlank()) {
                yaml = req.serviceYaml();
                resolvedFrom = "inline";
                hasDockerfile = true;
            } else {
                SourceMaterial mat = resolver.resolve(req.repoUrl(), req.branch());
                yaml = mat.serviceYaml();
                resolvedFrom = mat.resolvedFrom();
                hasDockerfile = mat.hasDockerfile();
            }
        } catch (DeployException e) {
            return new ValidationResult(false, List.of(e.getMessage()), null, "unresolved");
        }
        ServiceSpec spec;
        try {
            spec = parser.parse(yaml);
        } catch (Exception e) {
            return new ValidationResult(false, List.of("service.yaml 파싱 실패: " + e.getMessage()), null, resolvedFrom);
        }
        List<String> errors = validator.validate(spec, hasDockerfile, null);
        return new ValidationResult(errors.isEmpty(), errors, toSpecView(spec), resolvedFrom);
    }

    @Transactional
    public DeploymentResponse deploy(DeployRequest req) {
        Deployment d = deployments.save(new Deployment(req.programId(), req.repoUrl(), req.branch()));
        StringBuilder log = new StringBuilder();
        line(log, "배포 시작 · mode=" + props.mode());   // 대상 네임스페이스는 검증 후 신뢰도에 따라 결정
        try {
            d.setStatus(DeploymentStatus.VALIDATING);
            SourceMaterial mat = resolver.resolve(req.repoUrl(), req.branch());
            line(log, "소스 수집: " + mat.resolvedFrom());
            ServiceSpec spec = parser.parse(mat.serviceYaml());
            List<String> errors = validator.validate(spec, mat.hasDockerfile(), req.programId());
            if (!errors.isEmpty()) {
                errors.forEach(e -> line(log, "검증 오류: " + e));
                throw new DeployException("표준 규격 검증 실패");
            }
            d.setSlug(spec.slug());
            d.setName(spec.name());
            String tag = "d" + d.getId();
            d.setImageTag(tag);
            String image = renderer.imageRef(spec, tag);
            String ns = resolveNamespace(req.programId());   // 신뢰도별 네임스페이스
            line(log, "규격 검증 통과 · slug=" + spec.slug() + " · port=" + spec.port()
                    + " · health=" + spec.healthOrDefault() + " · namespace=" + ns);

            String url;
            if (props.isDocker()) {
                // 로컬 Docker 실배포: 실제로 이미지를 빌드하고 컨테이너를 띄운다.
                url = dockerDeploy(log, d, spec, mat, image, ns);
            } else {
                // 2. 이미지 빌드 — Kaniko 인클러스터 빌드(호스트 docker.sock 미사용, rootless)
                d.setStatus(DeploymentStatus.BUILDING);
                if (props.isReal()) {
                    String buildNs = props.buildNamespace();
                    String jobYaml = renderer.renderKanikoJob(spec, image, req.repoUrl(), req.branch(), buildNs);
                    Path jf = Files.createTempFile("edu-kaniko-", ".yaml");
                    Files.writeString(jf, jobYaml, StandardCharsets.UTF_8);
                    run(log, List.of("kubectl", "delete", "job", "build-" + spec.slug(),
                            "-n", buildNs, "--ignore-not-found"), null, 60);
                    if (!run(log, List.of("kubectl", "apply", "-f", jf.toString()), null, 60).ok())
                        throw new DeployException("Kaniko 빌드 Job 생성 실패");
                    line(log, "Kaniko 빌드 시작 · " + image + " (ns=" + buildNs + ", docker.sock 미사용)");
                    CommandRunner.Result wait = run(log, List.of("kubectl", "wait", "--for=condition=complete",
                            "job/build-" + spec.slug(), "-n", buildNs, "--timeout=600s"), null, 640);
                    if (!wait.ok()) throw new DeployException("Kaniko 빌드 실패(시간초과 또는 오류)");
                    line(log, "Kaniko 빌드·푸시 완료 · " + image);
                } else {
                    line(log, "[simulate] Kaniko 빌드 Job 생성 → " + image + " (docker.sock 미사용)");
                    line(log, "[simulate] kubectl wait job/build-" + spec.slug() + " --for=condition=complete");
                }

                // 3. 매니페스트 렌더링
                String manifest = renderer.render(spec, tag, ns);
                d.setManifest(manifest);
                line(log, "매니페스트 렌더링 완료 (Deployment/Service/Ingress) · ns=" + ns);

                // 4. K8s 적용
                d.setStatus(DeploymentStatus.DEPLOYING);
                if (props.isReal()) {
                    Path f = Files.createTempFile("edu-manifest-", ".yaml");
                    Files.writeString(f, manifest, StandardCharsets.UTF_8);
                    CommandRunner.Result r = run(log, List.of("kubectl", "apply", "-n", ns, "-f", f.toString()), null, 120);
                    if (!r.ok()) throw new DeployException("kubectl apply 실패");
                } else {
                    line(log, "[simulate] kubectl apply -n " + ns + " -f - (Deployment/Service/Ingress)");
                    line(log, "[simulate] 롤아웃 대기 및 헬스 체크(" + spec.healthOrDefault() + ") 통과 가정");
                }
                url = "https://" + props.ingressHost() + "/svc/" + spec.slug();
            }

            d.setUrl(url);
            d.setStatus(DeploymentStatus.RUNNING);
            line(log, "배포 완료 · " + url);
            publishLinkedProgram(req.programId(), req.actor(), url, d.getId());
        } catch (DeployException e) {
            d.setStatus(DeploymentStatus.FAILED);
            line(log, "배포 실패: " + e.getMessage());
        } catch (Exception e) {
            d.setStatus(DeploymentStatus.FAILED);
            line(log, "배포 오류: " + e.getMessage());
        }
        d.setLogText(log.toString());
        return toResponse(deployments.save(d));
    }

    @Transactional(readOnly = true)
    public DeploymentResponse latest(Long programId) {
        return deployments.findTopByProgramIdOrderByIdDesc(programId).map(d -> {
            // docker 모드에서 running 기록이라도 실제 컨테이너가 죽었으면 running으로 보고하지 않는다.
            if (props.isDocker() && d.getStatus() == DeploymentStatus.RUNNING && d.getSlug() != null) {
                CommandRunner.Result st = runner.run(
                        List.of("docker", "inspect", "-f", "{{.State.Running}}", "edu-svc-" + d.getSlug()), null, 15);
                boolean alive = st.output() != null && st.output().trim().startsWith("true");
                if (!alive) return toResponse(d, DeploymentStatus.FAILED);
            }
            return toResponse(d);
        }).orElse(null);
    }

    /** 로컬 Docker 데몬으로 이미지를 빌드하고 컨테이너를 실제로 띄운다. 접속 URL을 반환한다. */
    private String dockerDeploy(StringBuilder log, Deployment d, ServiceSpec spec, SourceMaterial mat, String image, String ns) {
        if (mat.workDir() == null) {
            throw new DeployException("도커 배포는 소스 작업 경로가 필요합니다. local:// 또는 실제 git 레포 주소를 사용하세요.");
        }
        if (!mat.hasDockerfile()) {
            throw new DeployException("Dockerfile 이 없어 이미지를 빌드할 수 없습니다.");
        }
        String container = "edu-svc-" + spec.slug();
        int hostPort = props.hostPortBase() + (int) (d.getId() % 2000);
        d.setHostPort(hostPort);

        d.setStatus(DeploymentStatus.BUILDING);
        if (!run(log, List.of("docker", "build", "-t", image, "."), new File(mat.workDir()), 600).ok()) {
            throw new DeployException("docker build 실패");
        }

        d.setStatus(DeploymentStatus.DEPLOYING);
        runner.run(List.of("docker", "rm", "-f", container), null, 30);   // 기존 컨테이너 정리(있으면)
        line(log, "$ docker rm -f " + container + "  (기존 컨테이너 정리)");
        CommandRunner.Result runRes = run(log, List.of(
                "docker", "run", "-d", "--name", container, "--restart", "unless-stopped",
                "-e", "PORT=" + spec.port(), "-p", hostPort + ":" + spec.port(), image), null, 120);
        if (!runRes.ok()) {
            throw new DeployException("docker run 실패");
        }
        CommandRunner.Result state = run(log, List.of("docker", "inspect", "-f", "{{.State.Running}}", container), null, 30);
        if (state.output() == null || !state.output().trim().startsWith("true")) {
            throw new DeployException("컨테이너가 정상 실행되지 않았습니다.");
        }
        d.setManifest(renderer.render(spec, d.getImageTag(), ns));   // 매니페스트도 참고용으로 보관(네임스페이스 반영)
        line(log, "컨테이너 실행 확인 · " + container + " (host 포트 " + hostPort + ")");
        return "http://" + props.appHost() + ":" + hostPort;
    }

    private void publishLinkedProgram(Long programId, String actor, String url, Long deploymentId) {
        if (programId == null) return;
        Program p = programs.findById(programId).orElse(null);
        if (p == null) return;
        p.setStatus(ProgramStatus.PUBLIC);
        p.setUpdatedAt(LocalDate.now());
        notifications.push(p.getOwner(), NotiKind.APPROVE,
                "「" + p.getName() + "」 이(가) 배포되어 공개되었습니다.",
                "운영 관리자 " + (actor != null && !actor.isBlank() ? actor : "정우성") + " · " + url,
                deploymentId);
    }

    private CommandRunner.Result run(StringBuilder log, List<String> cmd, File dir, long timeout) {
        line(log, "$ " + String.join(" ", cmd));
        CommandRunner.Result r = runner.run(cmd, dir, timeout);
        if (r.output() != null && !r.output().isBlank()) {
            for (String l : r.output().strip().split("\n")) line(log, "  " + l);
        }
        return r;
    }

    private void line(StringBuilder sb, String s) { sb.append("- ").append(s).append('\n'); }

    private SpecView toSpecView(ServiceSpec s) {
        return new SpecView(s.name(), s.slug(), s.category(), s.purposes(), s.tech(), s.summary(), s.port(), s.healthOrDefault());
    }

    private DeploymentResponse toResponse(Deployment d) {
        return toResponse(d, d.getStatus());
    }

    private DeploymentResponse toResponse(Deployment d, DeploymentStatus status) {
        return new DeploymentResponse(d.getId(), d.getProgramId(), d.getSlug(), d.getName(),
                status, d.getUrl(), d.getImageTag(), props.mode(), d.getManifest(), d.getLogText(), d.getCreatedAt());
    }
}
