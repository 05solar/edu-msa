package com.edu.msa.deploy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edu.msa.deploy.domain.ServiceSpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * P0-1 — Kaniko 비신뢰 빌드 격리 회귀 테스트.
 * 사용자 Dockerfile 의 RUN 은 임의 코드이므로, 렌더링된 빌드 Job 은 항상
 * 격리 네임스페이스(edu-build) + 전용 SA(토큰 미마운트) + 명시적 securityContext/
 * 리소스 상한을 갖춰야 한다. 이 계약이 깨지면 빌드가 플랫폼 권한/자원으로 샌다.
 */
@SpringBootTest
@ActiveProfiles("test")
class KanikoJobIsolationTest {

    @Autowired private ManifestRenderer renderer;
    @Autowired private DeployProperties props;

    private static ServiceSpec spec() {
        return new ServiceSpec("격리 테스트", "iso-test-svc", "doc",
                null, null, null, 8080, null, null, null, 0);
    }

    private String render() {
        return renderer.renderKanikoJob(spec(), "registry.edu.internal/iso-test-svc:d1",
                "https://github.com/user/repo", "main", props.buildNamespace());
    }

    @Test
    void 빌드_네임스페이스_기본값은_격리_전용_ns다() {
        assertEquals("edu-build", props.buildNamespace(),
                "빌드 ns 기본값이 플랫폼 ns 로 돌아가면 P0-1 격리가 무효화된다");
    }

    @Test
    void 렌더링된_Job은_격리_ns와_전용_SA_토큰_미마운트를_강제한다() {
        String yaml = render();
        assertTrue(yaml.contains("namespace: edu-build"), "빌드 Job 은 edu-build 에 생성돼야 한다");
        assertTrue(yaml.contains("serviceAccountName: edu-kaniko"), "기본 SA 를 쓰면 안 된다");
        assertTrue(yaml.contains("automountServiceAccountToken: false"), "SA 토큰이 마운트되면 안 된다");
        assertTrue(yaml.contains("enableServiceLinks: false"));
    }

    @Test
    void 렌더링된_Job은_명시적_securityContext와_리소스_상한을_갖는다() {
        String yaml = render();
        assertTrue(yaml.contains("allowPrivilegeEscalation: false"));
        assertTrue(yaml.contains("privileged: false"));
        assertTrue(yaml.contains("drop: [\"ALL\"]"), "capability 는 drop ALL 후 최소 추가여야 한다");
        assertTrue(yaml.contains("seccompProfile: { type: RuntimeDefault }"));
        assertTrue(yaml.contains("activeDeadlineSeconds"), "워커 유실 시 빌드 파드가 무한히 남으면 안 된다");
        assertTrue(yaml.contains("cpu: \"" + props.buildCpuLimit() + "\""), "CPU 상한 치환 누락");
        assertTrue(yaml.contains("memory: \"" + props.buildMemoryLimit() + "\""), "메모리 상한 치환 누락");
        assertTrue(yaml.contains("ephemeral-storage"), "임시디스크 상한 누락(디스크 고갈 방어)");
        assertFalse(yaml.contains("{{"), "미치환 플레이스홀더가 남으면 안 된다: " + yaml);
    }

    @Test
    void 레포_주소와_브랜치는_주입_방지_형식_검증을_통과해야_한다() {
        ServiceSpec s = spec();
        assertThrows(DeployException.class, () -> renderer.renderKanikoJob(
                s, "img:t", "https://github.com/user/repo; rm -rf /", "main", "edu-build"));
        assertThrows(DeployException.class, () -> renderer.renderKanikoJob(
                s, "img:t", "file:///etc/passwd", "main", "edu-build"));
        assertThrows(DeployException.class, () -> renderer.renderKanikoJob(
                s, "img:t", "https://github.com/user/repo", "main\"\n  evil: true", "edu-build"));
    }
}
