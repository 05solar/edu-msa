package com.edu.auth.ratelimit;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 기반 AttemptStore — 여러 auth-service replica 가 같은 카운터/차단 상태를 공유해
 * 동일한 rate limit 정책이 클러스터 전체에 적용된다.
 *
 * 연산 대응: increment = INCR(+최초 1회 EXPIRE), block = SET EX, remaining = TTL, reset = DEL.
 * 키는 "edu:rl:" 접두어 아래 LoginGuard 의 논리 키를 그대로 쓴다.
 * Redis 예외는 그대로 던진다 — 폴백은 FailoverAttemptStore 가 담당한다.
 */
public class RedisAttemptStore implements AttemptStore {

    private static final String PREFIX = "edu:rl:";
    private static final String BLOCK_SUFFIX = ":block";

    private final StringRedisTemplate redis;

    public RedisAttemptStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long increment(String key, long windowSeconds) {
        String k = PREFIX + key;
        Long count = redis.opsForValue().increment(k);
        if (count != null && count == 1) {
            // 최초 증가 시에만 윈도우 만료 설정(고정 윈도우). 경합 시 1을 받은 쪽이 설정한다.
            redis.expire(k, Duration.ofSeconds(windowSeconds));
        }
        return count == null ? 0 : count;
    }

    @Override
    public void block(String key, long blockSeconds) {
        redis.opsForValue().set(PREFIX + key + BLOCK_SUFFIX, "1", Duration.ofSeconds(blockSeconds));
    }

    @Override
    public long blockedRemainingSeconds(String key) {
        Long ttl = redis.getExpire(PREFIX + key + BLOCK_SUFFIX, TimeUnit.SECONDS);
        return ttl == null || ttl < 0 ? 0 : ttl;
    }

    @Override
    public void reset(String key) {
        redis.delete(PREFIX + key);
    }
}
