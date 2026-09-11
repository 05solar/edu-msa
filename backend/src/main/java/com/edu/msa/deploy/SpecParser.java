package com.edu.msa.deploy;

import com.edu.msa.deploy.domain.ServiceSpec;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * service.yaml 문자열을 ServiceSpec 으로 파싱한다.
 *
 * 입력은 비신뢰(임의 레포의 파일)다 — P1-2 하드닝:
 *  · SafeConstructor: 전역 타입 태그(!!java...)로 임의 자바 객체 생성 불가
 *  · LoaderOptions: alias 폭탄·과도한 중첩·초대형 문서·중복 키 거부
 *    (정상 service.yaml 은 1KB 미만·중첩 2단 — 제한값은 충분히 여유 있게 둔다)
 *  · 최상위/resources 필드 화이트리스트: 정의 밖 필드는 명시적으로 거부
 *  · load() 는 단일 문서만 허용(멀티 문서는 예외)
 * 모든 실패는 IllegalArgumentException("service.yaml ...") — 사용자에게는 필드/형식
 * 안내만 전달하고 스택은 서버 로그로만 남는다. 배포 파이프라인에서는 영구 오류로
 * 처리돼 재시도되지 않는다(DeploymentService).
 */
@Component
public class SpecParser {

    /**
     * MSA_SERVICE_SPEC.md 가 정의한 필드 전체 — 이 밖의 키는 거부한다.
     * "env" 는 규격 문서에 있으나 미구현(매니페스트에 반영되지 않음) — 기존 레포 호환을
     * 위해 허용하되 무시한다(문서에 미적용 명시).
     */
    private static final Set<String> TOP_KEYS = Set.of(
            "name", "slug", "category", "purposes", "tech", "summary", "port", "health", "resources", "env");
    private static final Set<String> RESOURCE_KEYS = Set.of("cpu", "memory", "gpu");

    @SuppressWarnings("unchecked")
    public ServiceSpec parse(String yamlContent) {
        Object loaded;
        try {
            LoaderOptions opts = new LoaderOptions();
            opts.setAllowDuplicateKeys(false);       // 중복 키 = 오류(마지막 값 조용히 채택 금지)
            opts.setMaxAliasesForCollections(10);    // alias 폭탄 차단
            opts.setNestingDepthLimit(10);           // 정상 규격은 2단
            opts.setCodePointLimit(64 * 1024);       // 정상 규격은 1KB 미만
            loaded = new Yaml(new SafeConstructor(opts)).load(yamlContent);
        } catch (RuntimeException e) {
            // 파서 예외(문법·태그·제한 위반)는 사용자용 한 줄로 변환 — 스택/내부 구조 비노출
            throw new IllegalArgumentException("service.yaml 을 읽을 수 없습니다: " + firstLine(e));
        }
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("service.yaml 형식이 올바르지 않습니다.");
        }
        Map<String, Object> m = (Map<String, Object>) map;
        for (Object k : m.keySet()) {
            if (!TOP_KEYS.contains(String.valueOf(k))) {
                throw new IllegalArgumentException(
                        "service.yaml: 허용되지 않는 필드입니다: " + k + " (허용: " + String.join(", ", TOP_KEYS) + ")");
            }
        }
        Map<String, Object> res = asMap(m.get("resources"));
        if (m.containsKey("resources") && res == null) {
            throw new IllegalArgumentException("service.yaml: resources 는 cpu/memory/gpu 를 갖는 맵이어야 합니다.");
        }
        if (res != null) {
            for (Object k : res.keySet()) {
                if (!RESOURCE_KEYS.contains(String.valueOf(k))) {
                    throw new IllegalArgumentException(
                            "service.yaml: resources 에 허용되지 않는 필드입니다: " + k + " (허용: cpu, memory, gpu)");
                }
            }
        }
        return new ServiceSpec(
                str(m.get("name")),
                str(m.get("slug")),
                str(m.get("category")),
                strList(m.get("purposes")),
                strList(m.get("tech")),
                str(m.get("summary")),
                intOr(m.get("port"), 0),
                str(m.get("health")),
                res != null ? str(res.get("cpu")) : null,
                res != null ? str(res.get("memory")) : null,
                res != null ? intOr(res.get("gpu"), 0) : 0
        );
    }

    /** 파서 예외 메시지의 첫 줄만(길이 제한) — 위치·태그 정보는 남기고 스택·클래스 나열은 버린다. */
    private static String firstLine(Exception e) {
        String msg = e.getMessage() == null ? "YAML 문법 오류" : e.getMessage();
        String line = msg.split("\n", 2)[0].trim();
        return line.length() > 160 ? line.substring(0, 160) + "…" : line;
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }

    private static int intOr(Object o, int fallback) {
        if (o instanceof Number n) return n.intValue();
        if (o == null) return fallback;
        try { return Integer.parseInt(String.valueOf(o).trim()); } catch (NumberFormatException e) { return fallback; }
    }

    @SuppressWarnings("unchecked")
    private static List<String> strList(Object o) {
        if (o instanceof List<?> list) return list.stream().map(String::valueOf).toList();
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }
}
