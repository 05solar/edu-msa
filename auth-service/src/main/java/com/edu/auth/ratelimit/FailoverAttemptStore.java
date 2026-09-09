package com.edu.auth.ratelimit;

import io.micrometer.core.instrument.Counter;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Redis(primary) 장애 시 인메모리(fallback)로 강등하는 AttemptStore.
 *
 * 보안 정책 관점: Redis 가 죽어도 fail-open(제한 해제)이 되지 않는다 —
 * 인스턴스-로컬 카운터로 계속 제한한다(정확도만 replica 단위로 느슨해짐).
 * 장애·복구는 1분 간격 로그와 edu.auth.ratelimit.failover 카운터로 드러난다.
 */
public class FailoverAttemptStore implements AttemptStore {

    private static final Logger log = LoggerFactory.getLogger(FailoverAttemptStore.class);
    private static final long LOG_INTERVAL_MS = 60_000;

    private final AttemptStore primary;
    private final AttemptStore fallback;
    private final Counter failoverCounter;
    private final AtomicLong lastLogAt = new AtomicLong(0);

    public FailoverAttemptStore(AttemptStore primary, AttemptStore fallback, Counter failoverCounter) {
        this.primary = primary;
        this.fallback = fallback;
        this.failoverCounter = failoverCounter;
    }

    @Override
    public long increment(String key, long windowSeconds) {
        return call(() -> primary.increment(key, windowSeconds),
                () -> fallback.increment(key, windowSeconds));
    }

    @Override
    public void block(String key, long blockSeconds) {
        call(() -> { primary.block(key, blockSeconds); return null; },
                () -> { fallback.block(key, blockSeconds); return null; });
    }

    @Override
    public long blockedRemainingSeconds(String key) {
        // 폴백 시에도 로컬 차단 상태는 함께 조회한다(Redis 복구 전에 등록된 로컬 차단 유지).
        return call(() -> Math.max(primary.blockedRemainingSeconds(key), fallback.blockedRemainingSeconds(key)),
                () -> fallback.blockedRemainingSeconds(key));
    }

    @Override
    public void reset(String key) {
        call(() -> { primary.reset(key); fallback.reset(key); return null; },
                () -> { fallback.reset(key); return null; });
    }

    private <T> T call(Supplier<T> primaryOp, Supplier<T> fallbackOp) {
        try {
            return primaryOp.get();
        } catch (Exception e) {
            failoverCounter.increment();
            long now = System.currentTimeMillis();
            long last = lastLogAt.get();
            if (now - last >= LOG_INTERVAL_MS && lastLogAt.compareAndSet(last, now)) {
                log.warn("rate-limit Redis 접근 실패 — 인메모리 폴백으로 계속 제한한다: {}", e.getMessage());
            }
            return fallbackOp.get();
        }
    }
}
