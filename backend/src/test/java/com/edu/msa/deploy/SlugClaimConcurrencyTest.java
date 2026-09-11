package com.edu.msa.deploy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edu.msa.deploy.domain.ServiceSpec;
import com.edu.msa.deploy.repository.SlugClaimRepository;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * P0-2 — slug TOCTOU 재현 + 원자적 예약(slug_claims) 검증.
 *
 * 재현: 애플리케이션 수준 exists 검사(ServiceSpecValidator)는 읽기일 뿐이라
 * 서로 다른 프로그램이 같은 slug 로 동시에 들어오면 둘 다 통과한다.
 * 수정: 소유권은 slug_claims 의 DB PK(INSERT 경쟁)가 최종 판정한다 —
 * JVM 락이 아니므로 replica 여러 개에서도 동일하게 동작한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class SlugClaimConcurrencyTest {

    @Autowired private ServiceSpecValidator validator;
    @Autowired private SlugClaims claims;
    @Autowired private SlugClaimRepository repo;

    private static ServiceSpec spec(String slug) {
        return new ServiceSpec("동시성", slug, "doc", null, null, null, 8080, null, null, null, 0);
    }

    @Test
    void 재현_애플리케이션_exists_검사만으로는_동시_요청_둘다_통과한다() throws Exception {
        String slug = "race-repro-svc";
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<List<String>> a = pool.submit(() -> {
                start.await();
                return validator.validate(spec(slug), true, 101L);
            });
            Future<List<String>> b = pool.submit(() -> {
                start.await();
                return validator.validate(spec(slug), true, 102L);
            });
            start.countDown();
            // 예약이 없는 상태의 검증은 둘 다 통과한다 — 이 검사는 UX 용일 뿐,
            // 동시성 정합성은 아래 claim(DB PK) 이 보장해야 한다는 것을 재현.
            assertTrue(a.get().isEmpty() && b.get().isEmpty(),
                    "TOCTOU 재현: exists 검사만으로는 동시 요청을 거르지 못한다");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 동일_slug_동시_예약은_정확히_하나만_성공한다() throws Exception {
        String slug = "race-claim-svc";
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger wins = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            var futures = new java.util.ArrayList<Future<?>>();
            for (int i = 0; i < threads; i++) {
                long programId = 200L + i;   // 전부 서로 다른 프로그램
                futures.add(pool.submit(() -> {
                    start.await();
                    if (claims.claim(slug, programId)) wins.incrementAndGet();
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) f.get();
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, wins.get(), "동일 slug 동시 예약은 정확히 1건만 성공해야 한다");
        assertTrue(repo.findById(slug).isPresent(), "승자의 예약 행이 존재해야 한다");
    }

    @Test
    void 같은_프로그램의_재예약은_멱등이고_다른_프로그램은_거부된다() {
        String slug = "race-owner-svc";
        assertTrue(claims.claim(slug, 301L), "최초 예약 성공");
        assertTrue(claims.claim(slug, 301L), "같은 프로그램 재배포는 허용(멱등)");
        assertFalse(claims.claim(slug, 302L), "다른 프로그램의 같은 slug 는 거부");
    }

    @Test
    void 예약된_slug는_validator_사전검사에서도_중복으로_걸린다() {
        String slug = "race-validator-svc";
        assertTrue(claims.claim(slug, 401L));
        assertTrue(validator.validate(spec(slug), true, 402L).stream()
                        .anyMatch(e -> e.contains("중복")),
                "다른 프로그램 시점에는 중복 오류");
        assertTrue(validator.validate(spec(slug), true, 401L).isEmpty(),
                "소유 프로그램의 재배포는 통과");
    }
}
