package com.edu.auth.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * RedisAttemptStore 의 Redis 원자 연산 매핑 검증
 * (INCR + 최초 1회 EXPIRE / SET EX / TTL / DEL — 키 접두어 edu:rl:).
 */
class RedisAttemptStoreTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private RedisAttemptStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        store = new RedisAttemptStore(redis);
    }

    @Test
    void 최초_증가시에만_윈도우_만료를_설정한다() {
        when(ops.increment("edu:rl:login:acct:u")).thenReturn(1L);
        assertEquals(1, store.increment("login:acct:u", 600));
        verify(redis).expire("edu:rl:login:acct:u", Duration.ofSeconds(600));

        when(ops.increment("edu:rl:login:acct:u")).thenReturn(2L);
        assertEquals(2, store.increment("login:acct:u", 600));
        verify(redis).expire(eq("edu:rl:login:acct:u"), eq(Duration.ofSeconds(600)));   // 여전히 1회뿐
    }

    @Test
    void 차단은_SET_EX_로_남은시간은_TTL_로_처리한다() {
        store.block("login:ip:1.2.3.4", 300);
        verify(ops).set("edu:rl:login:ip:1.2.3.4:block", "1", Duration.ofSeconds(300));

        when(redis.getExpire("edu:rl:login:ip:1.2.3.4:block", TimeUnit.SECONDS)).thenReturn(42L);
        assertEquals(42, store.blockedRemainingSeconds("login:ip:1.2.3.4"));

        // 키 없음(-2)·만료 없음(-1)은 차단 아님으로 본다
        when(redis.getExpire("edu:rl:login:ip:1.2.3.4:block", TimeUnit.SECONDS)).thenReturn(-2L);
        assertEquals(0, store.blockedRemainingSeconds("login:ip:1.2.3.4"));
    }

    @Test
    void reset_은_카운터_키만_삭제한다() {
        store.reset("login:acct:u");
        verify(redis).delete("edu:rl:login:acct:u");
        verify(redis, never()).delete("edu:rl:login:acct:u:block");
    }
}
