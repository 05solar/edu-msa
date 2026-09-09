package com.edu.auth.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Redis 장애 시 폴백 검증 — 보안 정책이 fail-open 되지 않고
 * 인메모리 카운터로 계속 제한되며, 폴백 횟수가 메트릭으로 집계된다.
 */
class FailoverAttemptStoreTest {

    /** 항상 실패하는 primary — Redis 전면 장애를 흉내낸다. */
    private static final class BrokenStore implements AttemptStore {
        @Override public long increment(String key, long windowSeconds) { throw new IllegalStateException("redis down"); }
        @Override public void block(String key, long blockSeconds) { throw new IllegalStateException("redis down"); }
        @Override public long blockedRemainingSeconds(String key) { throw new IllegalStateException("redis down"); }
        @Override public void reset(String key) { throw new IllegalStateException("redis down"); }
    }

    private Counter counter;
    private FailoverAttemptStore store;

    @BeforeEach
    void setUp() {
        counter = Counter.builder("edu.auth.ratelimit.failover").register(new SimpleMeterRegistry());
        store = new FailoverAttemptStore(new BrokenStore(), new InMemoryAttemptStore(), counter);
    }

    @Test
    void Redis_전면장애에도_인메모리로_카운트와_차단이_계속된다() {
        assertEquals(1, store.increment("login:acct:user1", 600));
        assertEquals(2, store.increment("login:acct:user1", 600));

        store.block("login:acct:user1", 300);
        assertTrue(store.blockedRemainingSeconds("login:acct:user1") > 0,
                "폴백 상태에서도 차단이 유지되어야 한다(fail-open 금지)");

        store.reset("login:acct:user1");
        assertEquals(1, store.increment("login:acct:user1", 600), "reset 후 카운터가 초기화된다");
    }

    @Test
    void 폴백_횟수가_메트릭으로_집계된다() {
        store.increment("k", 60);
        store.increment("k", 60);
        assertTrue(counter.count() >= 2, "폴백마다 카운터가 올라야 한다");
    }
}
