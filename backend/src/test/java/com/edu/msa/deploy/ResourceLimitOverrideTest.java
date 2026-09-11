package com.edu.msa.deploy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edu.msa.deploy.domain.ServiceSpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * P2-2 — 검증 상한과 렌더 limits 의 단일 출처(single source of truth) 증명.
 * 플랫폼 limit 설정(edu.deploy.cpu-limit/memory-limit)을 바꾸면 validator 의
 * 허용 상한과 렌더링되는 limits 가 "함께" 바뀌어야 한다. 서로 다른 설정을
 * 참조하면 이 테스트가 깨진다. quantity 등가(1 == 1000m, 1Gi == 1024Mi)도
 * 여기서 검증한다.
 */
@SpringBootTest(properties = {
        "edu.deploy.cpu-limit=1",          // 500m → 1 (=1000m)
        "edu.deploy.memory-limit=1Gi",     // 512Mi → 1Gi (=1024Mi)
})
@ActiveProfiles("test")
class ResourceLimitOverrideTest {

    @Autowired private ServiceSpecValidator validator;
    @Autowired private ManifestRenderer renderer;

    private static ServiceSpec spec(String cpu, String memory) {
        return new ServiceSpec("이름", "limit-ovr-svc", "doc", null, null, null, 8080, "/h", cpu, memory, 0);
    }

    @Test
    void 상향된_limit에_맞춰_허용_범위도_함께_커진다() {
        // 기본(500m/512Mi)에선 거부되던 값들이 새 limit(1/1Gi) 이하라 통과해야 한다
        assertTrue(validator.validate(spec("1000m", "1024Mi"), true, null).isEmpty(),
                "1000m == 1(limit), 1024Mi == 1Gi(limit) — 등가 비교로 정확히 limit 까지 허용");
        assertTrue(validator.validate(spec("1", "1Gi"), true, null).isEmpty(),
                "표기가 달라도(1 vs 1000m) 같은 양이면 같은 판정");
        assertTrue(validator.validate(spec("0.75", "700Mi"), true, null).isEmpty());
    }

    @Test
    void 새_limit_초과는_1단위라도_거부된다() {
        assertFalse(validator.validate(spec("1001m", null), true, null).isEmpty(), "1001m > 1000m");
        assertFalse(validator.validate(spec("1.001", null), true, null).isEmpty(), "1.001 > 1");
        assertFalse(validator.validate(spec(null, "1025Mi"), true, null).isEmpty(), "1025Mi > 1Gi");
        assertFalse(validator.validate(spec(null, "2Gi"), true, null).isEmpty());
    }

    @Test
    void 렌더링되는_limits도_같은_설정을_쓴다() {
        String out = renderer.render(spec("1000m", "1Gi"), "d1", "edu-services", 1L, 1L);
        assertTrue(out.contains("limits: { cpu: \"1\", memory: \"1Gi\""),
                "validator 가 허용한 상한과 렌더 limits 가 같은 프로퍼티에서 나와야 한다");
        assertTrue(out.contains("requests: { cpu: \"1000m\", memory: \"1Gi\""));
    }

    @Test
    void quantity_파서_등가_변환이_정확하다() {
        assertEquals(1000L, ServiceSpecValidator.parseCpuMillis("1").orElseThrow());
        assertEquals(1000L, ServiceSpecValidator.parseCpuMillis("1000m").orElseThrow());
        assertEquals(500L, ServiceSpecValidator.parseCpuMillis("0.5").orElseThrow());
        assertEquals(1024L, ServiceSpecValidator.parseMemoryMi("1Gi").orElseThrow());
        assertEquals(1024L, ServiceSpecValidator.parseMemoryMi("1024Mi").orElseThrow());
        assertTrue(ServiceSpecValidator.parseCpuMillis("1e3").isEmpty(), "지수 표기 등 문법 밖은 거부");
        assertTrue(ServiceSpecValidator.parseMemoryMi("256").isEmpty());
    }
}
