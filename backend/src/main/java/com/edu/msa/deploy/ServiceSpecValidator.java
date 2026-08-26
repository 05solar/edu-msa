package com.edu.msa.deploy;

import com.edu.msa.deploy.domain.ServiceSpec;
import com.edu.msa.deploy.repository.DeploymentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 표준 규격(MSA_SERVICE_SPEC.md) 정적 검증. */
@Component
public class ServiceSpecValidator {

    private static final Pattern SLUG = Pattern.compile("^[a-z][a-z0-9-]{1,38}$");
    private static final Set<String> CATEGORIES =
            Set.of("doc", "student", "curri", "budget", "facil", "data", "civil",
                    "hr", "asset", "safety", "report");

    private final DeploymentRepository deployments;

    public ServiceSpecValidator(DeploymentRepository deployments) {
        this.deployments = deployments;
    }

    /** 오류 목록을 반환한다(빈 목록이면 통과). currentProgramId 는 재배포 허용용(없으면 null). */
    public List<String> validate(ServiceSpec spec, boolean hasDockerfile, Long currentProgramId) {
        List<String> errors = new ArrayList<>();
        if (spec.name() == null || spec.name().isBlank()) {
            errors.add("service.yaml: name 이 필요합니다.");
        }
        if (spec.slug() == null || !SLUG.matcher(spec.slug()).matches()) {
            errors.add("service.yaml: slug 형식이 올바르지 않습니다. (^[a-z][a-z0-9-]{1,38}$)");
        } else {
            boolean dup = currentProgramId != null
                    ? deployments.existsBySlugAndProgramIdNot(spec.slug(), currentProgramId)
                    : deployments.existsBySlug(spec.slug());
            if (dup) {
                errors.add("service.yaml: slug 가 이미 다른 서비스와 중복됩니다: " + spec.slug());
            }
        }
        if (spec.category() == null || !CATEGORIES.contains(spec.category())) {
            errors.add("service.yaml: category 값이 올바르지 않습니다. (doc|student|curri|budget|facil|data|civil|hr|asset|safety|report)");
        }
        if (spec.port() < 1024 || spec.port() > 65535) {
            errors.add("service.yaml: port 는 1024~65535 범위여야 합니다.");
        }
        String health = spec.health();
        if (health != null && !health.isBlank() && !health.startsWith("/")) {
            errors.add("service.yaml: health 경로는 '/'로 시작해야 합니다.");
        }
        if (!hasDockerfile) {
            errors.add("레포 루트에 Dockerfile 이 없습니다.");
        }
        return errors;
    }
}
