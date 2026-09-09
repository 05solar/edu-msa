package com.edu.auth.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edu.auth.session.domain.RefreshToken;
import com.edu.auth.session.repository.RefreshTokenRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * refresh token 정리 정책 검증 — "만료 + 보존기간 경과" 행만 배치 삭제되고,
 * 만료 전(활성·폐기)·보존기간 내 만료 행은 남는다.
 * batch-size=2 로 줄여 배치 반복(2+1)도 함께 검증한다.
 */
@SpringBootTest(properties = {
        "edu.auth.cleanup.batch-size=2",
        "edu.auth.cleanup.retention-hours=24",
})
@ActiveProfiles("test")
@Transactional
class RefreshTokenCleanupTest {

    @Autowired private RefreshTokenCleaner cleaner;
    @Autowired private RefreshTokenRepository tokens;

    private RefreshToken save(String hash, OffsetDateTime expiresAt, boolean revoked) {
        RefreshToken t = new RefreshToken(1L, hash, expiresAt);
        if (revoked) t.revoke();
        return tokens.save(t);
    }

    @Test
    void 만료후_보존기간이_지난_행만_배치로_삭제된다() {
        OffsetDateTime now = OffsetDateTime.now();
        // 삭제 대상: 만료 + 24h 보존기간 경과 (3건 → batch 2 로 2회에 걸쳐 삭제)
        save("h-old-1", now.minusDays(2), false);
        save("h-old-2", now.minusDays(3), true);
        save("h-old-3", now.minusDays(10), false);
        // 보존 대상
        RefreshToken recentExpired = save("h-recent", now.minusHours(1), false);   // 만료됐지만 보존기간 내
        RefreshToken revokedAlive = save("h-revoked", now.plusDays(7), true);      // 폐기됐지만 만료 전(탈취 감지용)
        RefreshToken active = save("h-active", now.plusDays(7), false);            // 정상 세션

        int deleted = cleaner.cleanOnce();

        assertEquals(3, deleted);
        assertFalse(tokens.findByTokenHash("h-old-1").isPresent());
        assertFalse(tokens.findByTokenHash("h-old-2").isPresent());
        assertFalse(tokens.findByTokenHash("h-old-3").isPresent());
        assertTrue(tokens.findById(recentExpired.getId()).isPresent(), "보존기간 내 만료 행은 남아야 한다");
        assertTrue(tokens.findById(revokedAlive.getId()).isPresent(), "폐기됐지만 만료 전 행은 탈취 감지용으로 남아야 한다");
        assertTrue(tokens.findById(active.getId()).isPresent(), "활성 세션은 남아야 한다");
    }

    @Test
    void 지울_것이_없으면_0을_반환하고_아무_행도_건드리지_않는다() {
        RefreshToken active = save("h-none-active", OffsetDateTime.now().plusDays(7), false);
        assertEquals(0, cleaner.cleanOnce());
        assertTrue(tokens.findById(active.getId()).isPresent());
    }
}
