package com.edu.auth.ratelimit;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * 단일 인스턴스용 인메모리 AttemptStore.
 * 만료는 접근 시 검사(lazy)하고, 엔트리 수가 상한을 넘으면 만료분을 일괄 청소해
 * 대량 IP 유입으로 인한 메모리 증가를 막는다. 다중 인스턴스에서는 인스턴스별로
 * 독립 카운트되므로(느슨한 제한) 정확한 전역 제한이 필요해지면 Redis 구현으로 교체한다.
 */
@Component
public class InMemoryAttemptStore implements AttemptStore {

    private static final int MAX_ENTRIES = 100_000;

    private record Counter(AtomicLong count, long expiresAtMillis) {}

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();
    private final Map<String, Long> blockedUntil = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryAttemptStore() {
        this(Clock.systemUTC());
    }

    public InMemoryAttemptStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public long increment(String key, long windowSeconds) {
        cleanupIfOversized();
        long now = clock.millis();
        Counter c = counters.compute(key, (k, cur) ->
                (cur == null || cur.expiresAtMillis() <= now)
                        ? new Counter(new AtomicLong(0), now + windowSeconds * 1000)
                        : cur);
        return c.count().incrementAndGet();
    }

    @Override
    public void block(String key, long blockSeconds) {
        blockedUntil.put(key, clock.millis() + blockSeconds * 1000);
    }

    @Override
    public long blockedRemainingSeconds(String key) {
        Long until = blockedUntil.get(key);
        if (until == null) return 0;
        long remainMillis = until - clock.millis();
        if (remainMillis <= 0) {
            blockedUntil.remove(key);
            return 0;
        }
        // 남은 시간은 올림 — 방금 차단된 키가 0초로 보이지 않게 한다.
        return (remainMillis + 999) / 1000;
    }

    @Override
    public void reset(String key) {
        counters.remove(key);
    }

    private void cleanupIfOversized() {
        if (counters.size() < MAX_ENTRIES && blockedUntil.size() < MAX_ENTRIES) return;
        long now = clock.millis();
        counters.entrySet().removeIf(e -> e.getValue().expiresAtMillis() <= now);
        blockedUntil.entrySet().removeIf(e -> e.getValue() <= now);
    }
}
