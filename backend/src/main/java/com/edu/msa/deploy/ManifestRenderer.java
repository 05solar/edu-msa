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

    public String render(ServiceSpec spec, String imageTag) {
        return template
                .replace("{{SLUG}}", spec.slug())
                .replace("{{NAME}}", spec.name() == null ? spec.slug() : spec.name())
                .replace("{{IMAGE}}", imageRef(spec, imageTag))
                .replace("{{PORT}}", String.valueOf(spec.port()))
                .replace("{{HEALTH}}", spec.healthOrDefault())
                .replace("{{NAMESPACE}}", props.namespace())
                .replace("{{HOST}}", props.ingressHost())
                .replace("{{CATEGORY}}", spec.category() == null ? "" : spec.category())
                .replace("{{REPLICAS}}", String.valueOf(props.replicas()))
                .replace("{{CPU}}", spec.cpuOrDefault())
                .replace("{{MEMORY}}", spec.memoryOrDefault())
                .replace("{{CPU_LIMIT}}", props.cpuLimit())
                .replace("{{MEMORY_LIMIT}}", props.memoryLimit());
    }

    private String load() {
        try {
            return new String(new ClassPathResource("deploy-templates/service-template.yaml")
                    .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("서비스 템플릿을 로드하지 못했습니다.", e);
        }
    }
}
