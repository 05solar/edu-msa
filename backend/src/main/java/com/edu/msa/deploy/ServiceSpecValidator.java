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
    /**
     * 안전 상한: 테넌트 네임스페이스 LimitRange max(cpu 2 / memory 2Gi — hardening/10).
     * 실제 검증 상한은 이보다 낮은 "컨테이너 limit"(DeployProperties — 렌더 템플릿의
     * limits 와 동일 출처)이다. 유효 상한 = min(컨테이너 limit, LimitRange max).
     */
    private static final long LIMITRANGE_CPU_MAX_MILLIS = 2000;
    private static final long LIMITRANGE_MEMORY_MAX_MI = 2048;

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ServiceSpecValidator.class);

    private final SlugClaimRepository slugClaims;
    /** 요청 상한의 단일 출처 — ManifestRenderer 가 limits 로 쓰는 값과 같은 프로퍼티(P2-2). */
    private final long cpuLimitMillis;
    private final long memoryLimitMi;
    private final String cpuLimitRaw;
    private final String memoryLimitRaw;

    public ServiceSpecValidator(SlugClaimRepository slugClaims, DeployProperties props) {
        this.slugClaims = slugClaims;
        // 플랫폼 설정이 파싱 불가능하면 배포 검증 전체가 무의미하다 — 기동 시 fail-fast.
        this.cpuLimitRaw = props.cpuLimit();
        this.memoryLimitRaw = props.memoryLimit();
        this.cpuLimitMillis = parseCpuMillis(cpuLimitRaw)
                .orElseThrow(() -> new IllegalStateException(
                        "edu.deploy.cpu-limit 형식이 올바르지 않습니다: " + cpuLimitRaw));
        this.memoryLimitMi = parseMemoryMi(memoryLimitRaw)
                .orElseThrow(() -> new IllegalStateException(
                        "edu.deploy.memory-limit 형식이 올바르지 않습니다: " + memoryLimitRaw));
        // 플랫폼 설정 자체가 네임스페이스 LimitRange max 를 넘으면 배포가 LimitRange 에서
        // 거부된다 — 사용자 잘못이 아니므로 기동 시 경고로 드러낸다(동적 K8s 조회는 하지 않음).
        if (cpuLimitMillis > LIMITRANGE_CPU_MAX_MILLIS || memoryLimitMi > LIMITRANGE_MEMORY_MAX_MI) {
            log.warn("배포 컨테이너 limit({} / {})이 테넌트 LimitRange max(2 / 2Gi)를 초과합니다 — "
                    + "hardening/10-resourcequota-limits.yaml 과 EDU_DEPLOY_*_LIMIT 을 맞추세요.",
                    cpuLimitRaw, memoryLimitRaw);
        }
    }

    /** "500m"·"1"·"0.5" → 밀리코어. 형식 위반이면 empty. */
    static java.util.Optional<Long> parseCpuMillis(String v) {
        if (v == null || !CPU.matcher(v).matches()) return java.util.Optional.empty();
        long millis = v.endsWith("m")
                ? Long.parseLong(v.substring(0, v.length() - 1))
                : Math.round(Double.parseDouble(v) * 1000);
        return java.util.Optional.of(millis);
    }

    /** "512Mi"·"1Gi" → Mi. 형식 위반이면 empty. */
    static java.util.Optional<Long> parseMemoryMi(String v) {
        if (v == null || !MEMORY.matcher(v).matches()) return java.util.Optional.empty();
        long mi = v.endsWith("Gi")
                ? Long.parseLong(v.substring(0, v.length() - 2)) * 1024
                : Long.parseLong(v.substring(0, v.length() - 2));
        return java.util.Optional.of(mi);
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

    /**
     * 불변식(P2-2): 요청(request) <= 컨테이너 limit. 렌더 템플릿은 requests 에 사용자 값,
     * limits 에 플랫폼 설정을 넣으므로, 여기서 걸러지지 않으면 K8s apply 단계에서야
     * "requests must be <= limits" 로 실패한다(늦은 실패·빌드 자원 낭비).
     */
    private void validateCpu(String cpu, List<String> errors) {
        if (cpu == null || cpu.isBlank()) return;   // 미지정 → 기본값(cpuOrDefault ≤ limit)
        var millis = parseCpuMillis(cpu);
        if (millis.isEmpty()) {
            errors.add("service.yaml: resources.cpu 형식이 올바르지 않습니다. (예: 100m, 250m, 0.5)");
            return;
        }
        long effectiveMax = Math.min(cpuLimitMillis, LIMITRANGE_CPU_MAX_MILLIS);
        if (millis.get() <= 0) {
            errors.add("service.yaml: resources.cpu 는 0 보다 커야 합니다.");
        } else if (millis.get() > effectiveMax) {
            errors.add("service.yaml: CPU 요청값 " + cpu + " 은(는) 현재 서비스 최대 CPU 제한 "
                    + cpuLimitRaw + " 을(를) 초과합니다.");
        }
    }

    private void validateMemory(String memory, List<String> errors) {
        if (memory == null || memory.isBlank()) return;   // 미지정 → 기본값(memoryOrDefault ≤ limit)
        var mi = parseMemoryMi(memory);
        if (mi.isEmpty()) {
            errors.add("service.yaml: resources.memory 형식이 올바르지 않습니다. (예: 256Mi, 512Mi)");
            return;
        }
        long effectiveMax = Math.min(memoryLimitMi, LIMITRANGE_MEMORY_MAX_MI);
        if (mi.get() <= 0) {
            errors.add("service.yaml: resources.memory 는 0 보다 커야 합니다.");
        } else if (mi.get() > effectiveMax) {
            errors.add("service.yaml: Memory 요청값 " + memory + " 은(는) 현재 서비스 최대 메모리 제한 "
                    + memoryLimitRaw + " 을(를) 초과합니다.");
        }
    }
}
