package com.edu.msa.deploy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edu.msa.deploy.domain.ServiceSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * P1-2 — 비신뢰 service.yaml 파싱·렌더링 안전성.
 *
 * 계약: ManifestRenderer 는 raw 문자열 치환이므로 스스로를 방어하지 않는다 —
 * 검증(ServiceSpecValidator)을 통과한 값만 받는다. 따라서 구조를 바꿀 수 있는
 * 모든 문자(개행·따옴표·중괄호·콜론 등)는 검증 단계에서 반드시 거부돼야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ServiceSpecSecurityTest {

    @Autowired private SpecParser parser;
    @Autowired private ServiceSpecValidator validator;
    @Autowired private ManifestRenderer renderer;

    private static ServiceSpec spec(String name, String health, String cpu, String memory) {
        return new ServiceSpec(name, "sec-test-svc", "doc", null, null, null, 8080, health, cpu, memory, 0);
    }

    private List<String> validate(ServiceSpec s) {
        return validator.validate(s, true, null);
    }

    // ---------- 재현: raw 치환은 구조 변경이 가능하다(그래서 검증이 지켜야 한다) ----------

    @Test
    void 재현_악성_name이_렌더러에_직접_들어가면_YAML_구조가_변조된다() {
        // 검증을 우회해 렌더러에 직접 넣으면 annotation 주입이 실제로 성립함을 문서화한다.
        ServiceSpec evil = spec("x\"\n    edu.msa/evil: \"1", "/healthz", null, null);
        String out = renderer.render(evil, "d1", "edu-services", 1L, 1L);
        Map<String, Object> deployment = firstDoc(out);
        Map<String, Object> meta = asMap(asMap(deployment).get("metadata"));
        Map<String, Object> annotations = asMap(meta.get("annotations"));
        assertTrue(annotations.containsKey("edu.msa/evil"),
                "재현: 렌더러 단독으로는 주입이 성립한다 — 검증이 반드시 앞에서 거부해야 한다");
        // 같은 값은 검증 단계에서 반드시 거부된다.
        assertFalse(validate(evil).isEmpty(), "검증이 주입 문자열을 거부해야 한다");
    }

    // ---------- SnakeYAML 안전성 ----------

    @Test
    void 전역_타입_태그로_자바_객체를_만들_수_없다() {
        String yaml = "name: !!javax.script.ScriptEngineManager []\nslug: a-svc\n";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parser.parse(yaml));
        assertTrue(e.getMessage().startsWith("service.yaml"), "사용자용 오류 메시지여야 한다: " + e.getMessage());
    }

    @Test
    void alias_폭탄은_거부된다() {
        StringBuilder b = new StringBuilder("a: &a [\"x\",\"x\",\"x\",\"x\",\"x\"]\n");
        char prev = 'a';
        for (char c = 'b'; c <= 'i'; c++) {
            b.append(c).append(": &").append(c).append(" [")
             .append(("*" + prev + ",").repeat(20)).append("*").append(prev).append("]\n");
            prev = c;
        }
        assertThrows(IllegalArgumentException.class, () -> parser.parse(b.toString()));
    }

    @Test
    void 과도한_중첩은_거부된다() {
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse("a: " + "[".repeat(30) + "]".repeat(30)));
    }

    @Test
    void 중복_키는_거부된다() {
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse("name: a\nname: b\nslug: a-svc\n"));
    }

    @Test
    void 멀티_문서는_거부된다() {
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse("name: a\nslug: a-svc\n---\nkind: Pod\n"));
    }

    @Test
    void 허용되지_않은_최상위_필드는_거부된다() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parser.parse("name: a\nslug: a-svc\nprivileged: true\n"));
        assertTrue(e.getMessage().contains("privileged"));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse("name: a\nresources: { cpu: 1, hostPath: / }\n"));
    }

    // ---------- 필드 검증(주입 차단) ----------

    @Test
    void name_주입_문자들은_거부된다() {
        for (String bad : List.of(
                "x\"\n    edu.msa/evil: \"1",            // 개행 + 따옴표 탈출
                "x\", privileged: true",                  // 따옴표 탈출 + k8s 필드
                "x{a: b}",                                // flow map
                "x[1,2]",                                 // flow seq
                "x: y",                                   // 콜론
                "---",                                    // 문서 구분자
                "a".repeat(61))) {                        // 길이 초과
            assertFalse(validate(spec(bad, "/healthz", null, null)).isEmpty(),
                    "거부돼야 하는 name: " + bad.replace("\n", "\\n"));
        }
        for (String ok : List.of("근무일수(영업일) 계산기", "비품 QR 라벨 시트 생성기", "Doc-Proofreader v1.2")) {
            assertTrue(validate(spec(ok, "/healthz", null, null)).isEmpty(), "정상 name 이 거부됨: " + ok);
        }
    }

    @Test
    void health_경로는_URI_경로_문자만_허용한다() {
        for (String bad : List.of(
                "/h\n  hostNetwork: true",
                "/h}, port: 1, x: {y",
                "/h\", privileged: \"true",
                "healthz",                                 // '/' 미시작
                "http://evil/h",                           // scheme 삽입(콜론)
                "/h zz")) {                                // 공백
            assertFalse(validate(spec("이름", bad, null, null)).isEmpty(),
                    "거부돼야 하는 health: " + bad.replace("\n", "\\n"));
        }
        assertTrue(validate(spec("이름", "/api/v1/health-check_2", null, null)).isEmpty());
    }

    @Test
    void cpu는_k8s_quantity_형식과_상한만_허용한다() {
        for (String bad : List.of(
                "100m\", privileged: \"true",
                "100m\nhostNetwork: true",
                "{cpu: 1}",
                "-1", "999", "5000m", "3", "2.5",          // 음수·상한(2) 초과
                "1e3", "abc")) {
            assertFalse(validate(spec("이름", "/h", bad, null)).isEmpty(), "거부돼야 하는 cpu: " + bad);
        }
        for (String ok : List.of("100m", "500m", "1", "2", "0.5", "1500m")) {
            assertTrue(validate(spec("이름", "/h", ok, null)).isEmpty(), "정상 cpu 가 거부됨: " + ok);
        }
    }

    @Test
    void memory는_k8s_quantity_형식과_상한만_허용한다() {
        for (String bad : List.of(
                "256Mi\", x: \"y",
                "1Gi\nprivileged: true",
                "-256Mi", "3Gi", "4096Mi",                  // 음수·상한(2Gi) 초과
                "256", "256mb", "1G i", "{a: 1}")) {
            assertFalse(validate(spec("이름", "/h", null, bad)).isEmpty(), "거부돼야 하는 memory: " + bad);
        }
        for (String ok : List.of("128Mi", "256Mi", "1Gi", "2Gi", "2048Mi")) {
            assertTrue(validate(spec("이름", "/h", null, ok)).isEmpty(), "정상 memory 가 거부됨: " + ok);
        }
    }

    // ---------- 렌더 결과 무결성 ----------

    @Test
    void 정상_spec_렌더_결과는_예상_구조와_정확히_일치한다() {
        ServiceSpec s = spec("정상 서비스", "/healthz", "250m", "256Mi");
        assertTrue(validate(s).isEmpty());
        String out = renderer.render(s, "d7", "edu-services", 42L, 7L);

        List<Object> docs = new ArrayList<>();
        new org.yaml.snakeyaml.Yaml().loadAll(out).forEach(docs::add);
        assertEquals(5, docs.size(), "테넌트 매니페스트는 정확히 5개 문서여야 한다");
        List<String> kinds = docs.stream().map(d -> String.valueOf(asMap(d).get("kind"))).toList();
        assertEquals(List.of("Deployment", "Service", "Ingress", "HorizontalPodAutoscaler",
                "PodDisruptionBudget"), kinds);

        Map<String, Object> dep = asMap(docs.get(0));
        Map<String, Object> podSpec = asMap(asMap(asMap(asMap(dep.get("spec")).get("template")).get("spec")));
        List<?> containers = (List<?>) podSpec.get("containers");
        Map<String, Object> c0 = asMap(containers.get(0));
        assertEquals("registry.edu.internal/sec-test-svc:d7", c0.get("image"));
        assertEquals("/healthz", asMap(asMap(c0.get("readinessProbe")).get("httpGet")).get("path"));
        assertEquals("250m", asMap(asMap(c0.get("resources")).get("requests")).get("cpu"));
        assertEquals("256Mi", asMap(asMap(c0.get("resources")).get("requests")).get("memory"));
        // 사용자 입력으로 제어되면 안 되는 필드가 생기지 않았는지
        for (String forbidden : List.of("hostNetwork", "hostPID", "nodeSelector", "tolerations",
                "serviceAccountName", "imagePullSecrets")) {
            assertFalse(podSpec.containsKey(forbidden), "예상 외 필드 생성: " + forbidden);
        }
        assertEquals(Boolean.FALSE, podSpec.get("automountServiceAccountToken"));
    }

    // ---------- helpers ----------

    private Map<String, Object> firstDoc(String yaml) {
        return asMap(new org.yaml.snakeyaml.Yaml().loadAll(yaml).iterator().next());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        assertNotNull(o, "예상 위치에 맵이 없다");
        return (Map<String, Object>) o;
    }
}
