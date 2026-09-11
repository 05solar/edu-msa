package com.edu.auth.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edu.auth.account.domain.Account;
import com.edu.auth.account.domain.AccountRole;
import com.edu.auth.account.repository.AccountRepository;
import com.edu.auth.common.UnauthorizedException;
import com.edu.auth.session.domain.RefreshToken;
import com.edu.auth.session.dto.AuthDtos.IssuedTokens;
import com.edu.auth.session.repository.RefreshTokenRepository;
import com.edu.auth.token.JwtTokenProvider;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * P1-3 — refresh token 회전의 동시성 불변식.
 *
 * 불변식: 동일 refresh token 은 정확히 한 번만 회전에 성공한다.
 * 동시에 N개 요청이 와도 성공 1 · 실패 N-1 이고, 새 refresh token 도 1개만 생긴다.
 * 원자성은 DB 조건부 UPDATE(consumeIfUsable)가 보장한다 — JVM 락 없음(멀티 replica 안전).
 *
 * 탈취 감지 의미: 이미(과거에) 폐기된 토큰의 재제출은 계정 전 세션 폐기(기존 정책 유지).
 * 동시 회전의 "지금 막" 패배는 탈취로 오인하지 않는다 — 승자의 새 세션은 살아 있어야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class RefreshRotationConcurrencyTest {

    @Autowired private AuthService auth;
    @Autowired private AccountRepository accounts;
    @Autowired private RefreshTokenRepository tokens;
    @Autowired private JwtTokenProvider jwt;

    private Account account;

    @BeforeEach
    void setUp() {
        tokens.deleteAll();
        account = accounts.findByUsername("rot-user").orElseGet(() ->
                accounts.save(new Account("rot-user", "x", "회전 테스터", "rot@test.local",
                        "테스트과", AccountRole.USER, false)));
    }

    /** 저장된 usable refresh token 원문을 만든다(issue 흐름과 동일: 원문 발급 + 해시 저장). */
    private String issuedRefreshToken(long ttlSeconds) {
        String raw = jwt.createRefreshToken(account, ttlSeconds);
        tokens.save(new RefreshToken(account.getId(), JwtTokenProvider.hash(raw),
                OffsetDateTime.now().plusSeconds(ttlSeconds)));
        return raw;
    }

    private long activeCount() {
        return tokens.findAll().stream()
                .filter(t -> t.getAccountId().equals(account.getId()) && t.isUsable())
                .count();
    }

    @Test
    void 동시_2요청은_정확히_1개만_성공한다() throws Exception {
        String raw = issuedRefreshToken(600);
        int threads = 2;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> fs = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                fs.add(pool.submit(() -> {
                    start.await();
                    try {
                        auth.refresh(raw);
                        success.incrementAndGet();
                    } catch (UnauthorizedException expected) { /* 패자 */ }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : fs) f.get();
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, success.get(), "동일 토큰 동시 사용은 정확히 1번만 성공해야 한다");
        assertEquals(1, activeCount(), "새 refresh token 도 정확히 1개만 생성돼야 한다");
    }

    @Test
    void 동시_10요청도_성공1_실패9_신규토큰1개다() throws Exception {
        String raw = issuedRefreshToken(600);
        int threads = 10;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        List<IssuedTokens> issued = java.util.Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> fs = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                fs.add(pool.submit(() -> {
                    start.await();
                    try {
                        issued.add(auth.refresh(raw));
                        success.incrementAndGet();
                    } catch (UnauthorizedException expected) { /* 패자 */ }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : fs) f.get();
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, success.get(), "성공은 1개");
        assertEquals(1, activeCount(), "usable 토큰(승자 발급분)은 정확히 1개");
        // 정상 동시성의 패자들이 탈취로 오인돼 승자 세션까지 폐기되면 안 된다 —
        // 승자가 받은 새 토큰으로 후속 refresh 가 성공해야 한다.
        IssuedTokens winner = issued.get(0);
        auth.refresh(winner.refreshToken());
        assertEquals(1, activeCount(), "승자 토큰의 후속 회전도 정상 동작(전 세션 폐기 없음)");
    }

    @Test
    void 회전_직후_재사용은_실패하지만_grace_창_안이라_승자_세션은_유지된다() {
        String r1 = issuedRefreshToken(600);
        IssuedTokens second = auth.refresh(r1);          // R1 → R2 회전
        assertEquals(1, activeCount());
        // 즉시 재사용 — 동시 경쟁 패배와 구분 불가한 창(grace) 안 → 401, 전 세션 폐기 없음
        assertThrows(UnauthorizedException.class, () -> auth.refresh(r1));
        assertEquals(1, activeCount(), "grace 창 안 재사용은 승자 세션을 끊지 않는다");
        // 승자 토큰은 계속 정상 회전된다
        auth.refresh(second.refreshToken());
        assertEquals(1, activeCount());
    }

    @Test
    void 오래전_폐기된_토큰의_재사용은_탈취로_간주해_전_세션을_폐기한다(
            @Autowired javax.sql.DataSource dataSource) throws Exception {
        String r1 = issuedRefreshToken(600);
        IssuedTokens second = auth.refresh(r1);          // R1 → R2 회전
        assertEquals(1, activeCount());
        // R1 의 폐기 시각을 grace 창 밖(5분 전)으로 되돌려 "오래된 재사용 공격"을 시뮬레이션
        try (var conn = dataSource.getConnection();
             var st = conn.prepareStatement(
                     "update refresh_tokens set revoked_at = ? where token_hash = ?")) {
            st.setObject(1, OffsetDateTime.now().minusMinutes(5));
            st.setString(2, JwtTokenProvider.hash(r1));
            assertEquals(1, st.executeUpdate());
        }
        assertThrows(UnauthorizedException.class, () -> auth.refresh(r1));
        assertEquals(0, activeCount(), "탈취 의심 — 승자 토큰(R2) 포함 전 세션 폐기");
        assertThrows(UnauthorizedException.class, () -> auth.refresh(second.refreshToken()),
                "폐기된 R2 도 더 이상 쓸 수 없어야 한다");
    }

    @Test
    void 만료_토큰은_실패하고_새_토큰이_생기지_않는다() {
        String raw = jwt.createRefreshToken(account, 600);
        tokens.save(new RefreshToken(account.getId(), JwtTokenProvider.hash(raw),
                OffsetDateTime.now().minusSeconds(5)));   // 이미 만료
        assertThrows(UnauthorizedException.class, () -> auth.refresh(raw));
        assertEquals(0, activeCount(), "만료 토큰으로는 어떤 새 토큰도 발급되지 않는다");
    }

    @Test
    void logout_후_refresh는_실패한다() {
        String raw = issuedRefreshToken(600);
        auth.logout(raw);
        assertThrows(UnauthorizedException.class, () -> auth.refresh(raw));
        assertEquals(0, activeCount());
    }

    @Test
    void 위조_토큰은_500없이_401이다() {
        assertThrows(UnauthorizedException.class, () -> auth.refresh("not-a-jwt"));
        assertThrows(UnauthorizedException.class, () -> auth.refresh(null));
        assertThrows(UnauthorizedException.class, () -> auth.refresh(" "));
    }
}
