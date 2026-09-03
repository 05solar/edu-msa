package com.edu.msa.deploy;

import com.edu.msa.deploy.domain.ServiceSpec;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** ServiceSpec + 설정으로 K8s 매니페스트(Deployment/Service/Ingress)를 렌더링한다. */
@Component
public class ManifestRenderer {

    private final DeployProperties props;
    private final String template;

    public ManifestRenderer(DeployProperties props) {
        this.props = props;
        this.template = load();
    }

    public String imageRef(ServiceSpec spec, String tag) {
        return props.registry() + "/" + spec.slug() + ":" + tag;
    }

    public String render(ServiceSpec spec, String imageTag, String namespace) {
        return template
                .replace("{{SLUG}}", spec.slug())
                .replace("{{NAME}}", spec.name() == null ? spec.slug() : spec.name())
                .replace("{{IMAGE}}", imageRef(spec, imageTag))
                .replace("{{PORT}}", String.valueOf(spec.port()))
                .replace("{{HEALTH}}", spec.healthOrDefault())
                .replace("{{NAMESPACE}}", namespace)
                .replace("{{HOST}}", props.ingressHost())
                .replace("{{CATEGORY}}", spec.category() == null ? "" : spec.category())
                .replace("{{REPLICAS}}", String.valueOf(props.replicas()))
                .replace("{{CPU}}", spec.cpuOrDefault())
                .replace("{{MEMORY}}", spec.memoryOrDefault())
                .replace("{{CPU_LIMIT}}", props.cpuLimit())
                .replace("{{MEMORY_LIMIT}}", props.memoryLimit())
                // GPU 요청 서비스만 nvidia.com/gpu 리소스를 limits 에 추가한다(그 외 빈 문자열).
                // GPU 스케줄링에는 NVIDIA GPU Operator/device-plugin 이 설치돼 있어야 한다.
                .replace("{{GPU_LIMIT}}",
                        spec.usesGpu() ? ", \"nvidia.com/gpu\": \"" + spec.gpu() + "\"" : "");
    }

    // 미신뢰 업로더 입력이 Kaniko 인자/YAML 로 주입되지 않도록 엄격 검증한다.
    private static final java.util.regex.Pattern REPO_RE =
            java.util.regex.Pattern.compile("^https?://[A-Za-z0-9._-]+(:\\d+)?(/[A-Za-z0-9._-]+)+(\\.git)?$");
    private static final java.util.regex.Pattern BRANCH_RE =
            java.util.regex.Pattern.compile("^[A-Za-z0-9._/-]{1,120}$");

    /** Kaniko 빌드 Job 매니페스트 렌더링(real 모드, docker.sock 미사용). */
    public String renderKanikoJob(ServiceSpec spec, String image, String repoUrl, String branch, String namespace) {
        if (repoUrl == null || !REPO_RE.matcher(repoUrl).matches())
            throw new DeployException("허용되지 않는 레포 주소 형식: " + repoUrl);
        String br = (branch != null && !branch.isBlank()) ? branch : "main";
        if (!BRANCH_RE.matcher(br).matches())
            throw new DeployException("허용되지 않는 브랜치 이름: " + branch);
        // 내부 Gitea 는 clone-base(내부 접근 주소)로 재작성해 받는다 — 재작성 결과도 형식 재검증.
        String fetchUrl = props.rewriteGiteaUrl(repoUrl);
        if (!REPO_RE.matcher(fetchUrl).matches())
            throw new DeployException("허용되지 않는 clone-base 주소 형식");
        String ctx = "git://" + fetchUrl.replaceFirst("^https?://", "") + "#refs/heads/" + br;
        return load("deploy-templates/kaniko-job.yaml")
                .replace("{{SLUG}}", spec.slug())
                .replace("{{NAMESPACE}}", namespace)
                .replace("{{IMAGE}}", image)
                .replace("{{CONTEXT}}", ctx)
                // HTTP(비TLS) 레지스트리 지원 — edu.deploy.kaniko-insecure=true 일 때만
                .replace("{{EXTRA_ARGS}}",
                        props.kanikoInsecure() ? "- \"--insecure\"\n            - \"--insecure-pull\"" : "")
                // 내부 Gitea 비공개 레포일 때만 봇 자격 증명 env 를 Secret 참조로 주입한다
                // (매니페스트에 토큰 평문이 들어가지 않음). 그 외 레포는 빈 문자열.
                .replace("{{GIT_CRED_ENV}}", props.isGiteaRepo(repoUrl) ? GIT_CRED_ENV : "");
    }

    /** Kaniko git 컨텍스트 자격 증명 — edu-gitea-token Secret(username/token) 참조.
     *  들여쓰기는 kaniko-job.yaml 의 컨테이너 키(10칸)에 맞춘다. */
    private static final String GIT_CRED_ENV =
            "          env:\n"
            + "            - name: GIT_USERNAME\n"
            + "              valueFrom: { secretKeyRef: { name: edu-gitea-token, key: username, optional: true } }\n"
            + "            - name: GIT_PASSWORD\n"
            + "              valueFrom: { secretKeyRef: { name: edu-gitea-token, key: token, optional: true } }";

    private String load() {
        return load("deploy-templates/service-template.yaml");
    }

    private String load(String path) {
        try {
            return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("템플릿 로드 실패: " + path, e);
        }
    }
}
