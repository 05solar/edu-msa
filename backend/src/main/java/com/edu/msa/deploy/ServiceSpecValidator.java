package com.edu.msa.deploy;

import com.edu.msa.deploy.domain.ServiceSpec;
import com.edu.msa.deploy.repository.SlugClaimRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 표준 규격(MSA_SERVICE_SPEC.md) 정적 검증. */
@Component
public class ServiceSpecValidator {

    private static final Pattern SLUG = Pattern.compile("^[a-z][a-z0-9-]{1,38}$");
    private static final Set<String> CATEGORIES =
            Set.of("doc", "student", "curri", "budget", "facil", "data", "civil");

    // [P1-2] 아래 값들은 ManifestRenderer 가 K8s 매니페스트에 raw 치환한다 — 구조를 바꿀 수
    // 있는 문자(개행·따옴표·중괄호·콜론 등)는 여기서 전부 거부해야 한다(렌더러는 무방비).
    /** name: 표시용 문자열(annotation) — 글자/숫자로 시작, 글자/숫자/공백/일부 구두점만 60자 이내. */
    private static final Pattern NAME = Pattern.compile("^[\\p{L}\\p{N}][\\p{L}\\p{N} ._()·-]{0,59}$");
    /** health: URI 경로 — '/' 시작, 경로 안전 문자만. */
    private static final Pattern HEALTH = Pattern.compile("^/[A-Za-z0-9._/-]{0,127}$");
    /** cpu: K8s quantity 부분집합 — 밀리코어(m) 또는 소수 코어. */
    private static final Pattern CPU = Pattern.compile("^([0-9]{1,5}m|[0-9](\\.[0-9]{1,3})?)$");
    /** memory: K8s quantity 부분집합 — Mi/Gi 만. */
    private static final Pattern MEMORY = Pattern.compile("^[0-9]{1,5}(Mi|Gi)$");
    /** 상한: 테넌트 LimitRange max(cpu 2 / memory 2Gi — hardening/10)와 일치시킨다. */
    private static final long CPU_MAX_MILLIS = 2000;
    private static final long MEMORY_MAX_MI = 2048;

    private final SlugClaimRepository slugClaims;

    public ServiceSpecValidator(SlugClaimRepository slugClaims) {
        this.slugClaims = slugClaims;
    }

    /** 오류 목록을 반환한다(빈 목록이면 통과). currentProgramId 는 재배포 허용용(없으면 null). */
    public List<String> validate(ServiceSpec spec, boolean hasDockerfile, Long currentProgramId) {
        List<String> errors = new ArrayList<>();
        if (spec.name() == null || spec.name().isBlank()) {
            errors.add("service.yaml: name 이 필요합니다.");
        } else if (!NAME.matcher(spec.name()).matches()) {
            errors.add("service.yaml: name 은 글자·숫자·공백·._()- 만 60자 이내로 허용됩니다.");
        }
        if (spec.slug() == null || !SLUG.matcher(spec.slug()).matches()) {
            errors.add("service.yaml: slug 형식이 올바르지 않습니다. (^[a-z][a-z0-9-]{1,38}$)");
        } else {
            // UX 용 사전 검사 — 동시성 정합성은 배포 시점의 slug_claims INSERT(SlugClaims.claim)가
            // DB PK 로 최종 보장한다(이 읽기 검사만으로는 TOCTOU 를 막을 수 없다).
            boolean dup = slugClaims.findById(spec.slug())
                    .map(c -> currentProgramId == null
                            || !Objects.equals(c.getProgramId(), currentProgramId))
                    .orElse(false);
            if (dup) {
                errors.add("service.yaml: slug 가 이미 다른 서비스와 중복됩니다: " + spec.slug());
            }
        }
        if (spec.category() == null || !CATEGORIES.contains(spec.category())) {
            errors.add("service.yaml: category 값이 올바르지 않습니다. (doc|student|curri|budget|facil|data|civil)");
        }
        if (spec.port() < 1024 || spec.port() > 65535) {
            errors.add("service.yaml: port 는 1024~65535 범위여야 합니다.");
        }
        String health = spec.health();
        if (health != null && !health.isBlank() && !HEALTH.matcher(health).matches()) {
            errors.add("service.yaml: health 는 '/'로 시작하는 URI 경로(영숫자·._/-)여야 합니다.");
        }
        validateCpu(spec.cpu(), errors);
        validateMemory(spec.memory(), errors);
        if (spec.gpu() < 0 || spec.gpu() > 8) {
            errors.add("service.yaml: resources.gpu 는 0~8 범위여야 합니다. (GPU 미사용은 0 또는 생략)");
        }
        if (!hasDockerfile) {
            errors.add("레포 루트에 Dockerfile 이 없습니다.");
        }
        return errors;
    }

    private static void validateCpu(String cpu, List<String> errors) {
        if (cpu == null || cpu.isBlank()) return;   // 미지정 → 기본값(cpuOrDefault)
        if (!CPU.matcher(cpu).matches()) {
            errors.add("service.yaml: resources.cpu 형식이 올바르지 않습니다. (예: 100m, 500m, 1, 2)");
            return;
        }
        long millis = cpu.endsWith("m")
                ? Long.parseLong(cpu.substring(0, cpu.length() - 1))
                : Math.round(Double.parseDouble(cpu) * 1000);
        if (millis <= 0 || millis > CPU_MAX_MILLIS) {
            errors.add("service.yaml: resources.cpu 는 0 초과 ~ 최대 2 (2000m) 까지 허용됩니다.");
        }
    }

    private static void validateMemory(String memory, List<String> errors) {
        if (memory == null || memory.isBlank()) return;   // 미지정 → 기본값(memoryOrDefault)
        if (!MEMORY.matcher(memory).matches()) {
            errors.add("service.yaml: resources.memory 형식이 올바르지 않습니다. (예: 256Mi, 1Gi)");
            return;
        }
        long mi = memory.endsWith("Gi")
                ? Long.parseLong(memory.substring(0, memory.length() - 2)) * 1024
                : Long.parseLong(memory.substring(0, memory.length() - 2));
        if (mi <= 0 || mi > MEMORY_MAX_MI) {
            errors.add("service.yaml: resources.memory 는 0 초과 ~ 최대 2Gi (2048Mi) 까지 허용됩니다.");
        }
    }
}
