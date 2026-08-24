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
import com.edu.msa.deploy.repository.DeploymentRepository;
import com.edu.msa.notification.NotificationService;
import com.edu.msa.program.domain.Program;
import com.edu.msa.program.repository.ProgramRepository;
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

    public DeploymentService(SourceResolver resolver, SpecParser parser, ServiceSpecValidator validator,
                             ManifestRenderer renderer, DeploymentRepository deployments, DeployProperties props,
                             CommandRunner runner, ProgramRepository programs, NotificationService notifications) {
        this.resolver = resolver;
        this.parser = parser;
        this.validator = validator;
        this.renderer = renderer;
        this.deployments = deployments;
        this.props = props;
        this.runner = runner;
        this.programs = programs;
        this.notifications = notifications;
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
        List<String> errors = validator.validate(spec, hasDockerfile);
        return new ValidationResult(errors.isEmpty(), errors, toSpecView(spec), resolvedFrom);
    }

    @Transactional
    public DeploymentResponse deploy(DeployRequest req) {
        Deployment d = deployments.save(new Deployment(req.programId(), req.repoUrl(), req.branch()));
        StringBuilder log = new StringBuilder();
        line(log, "배포 시작 · mode=" + props.mode() + " · namespace=" + props.namespace());
        try {
            d.setStatus(DeploymentStatus.VALIDATING);
            SourceMaterial mat = resolver.resolve(req.repoUrl(), req.branch());
            line(log, "소스 수집: " + mat.resolvedFrom());
            ServiceSpec spec = parser.parse(mat.serviceYaml());
            List<String> errors = validator.validate(spec, mat.hasDockerfile());
            if (!errors.isEmpty()) {
                errors.forEach(e -> line(log, "검증 오류: " + e));
                throw new DeployException("표준 규격 검증 실패");
            }
            d.setSlug(spec.slug());
            d.setName(spec.name());
            String tag = "d" + d.getId();
            d.setImageTag(tag);
            String image = renderer.imageRef(spec, tag);
            line(log, "규격 검증 통과 · slug=" + spec.slug() + " · port=" + spec.port() + " · health=" + spec.healthOrDefault());

            // 2. 이미지 빌드
            d.setStatus(DeploymentStatus.BUILDING);
            if (props.isReal() && mat.workDir() != null) {
                run(log, List.of("docker", "build", "-t", image, "."), new File(mat.workDir()), 600);
                run(log, List.of("docker", "push", image), null, 600);
            } else {
                line(log, "[simulate] docker build -t " + image + " " + safeDir(mat.workDir()));
                line(log, "[simulate] docker push " + image);
            }

            // 3. 매니페스트 렌더링
            String manifest = renderer.render(spec, tag);
            d.setManifest(manifest);
            line(log, "매니페스트 렌더링 완료 (Deployment/Service/Ingress)");

            // 4. K8s 적용
            d.setStatus(DeploymentStatus.DEPLOYING);
            if (props.isReal()) {
                Path f = Files.createTempFile("edu-manifest-", ".yaml");
                Files.writeString(f, manifest, StandardCharsets.UTF_8);
                CommandRunner.Result r = run(log, List.of("kubectl", "apply", "-n", props.namespace(), "-f", f.toString()), null, 120);
                if (!r.ok()) throw new DeployException("kubectl apply 실패");
            } else {
                line(log, "[simulate] kubectl apply -n " + props.namespace() + " -f - (Deployment/Service/Ingress)");
                line(log, "[simulate] 롤아웃 대기 및 헬스 체크(" + spec.healthOrDefault() + ") 통과 가정");
            }

            // 5. 공개 전환
            String url = "https://" + props.ingressHost() + "/svc/" + spec.slug();
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
        return deployments.findTopByProgramIdOrderByIdDesc(programId).map(this::toResponse).orElse(null);
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

    private String safeDir(String dir) { return dir == null ? "." : dir; }

    private void line(StringBuilder sb, String s) { sb.append("- ").append(s).append('\n'); }

    private SpecView toSpecView(ServiceSpec s) {
        return new SpecView(s.name(), s.slug(), s.category(), s.purposes(), s.tech(), s.summary(), s.port(), s.healthOrDefault());
    }

    private DeploymentResponse toResponse(Deployment d) {
        return new DeploymentResponse(d.getId(), d.getProgramId(), d.getSlug(), d.getName(),
                d.getStatus(), d.getUrl(), d.getImageTag(), props.mode(), d.getManifest(), d.getLogText(), d.getCreatedAt());
    }
}
