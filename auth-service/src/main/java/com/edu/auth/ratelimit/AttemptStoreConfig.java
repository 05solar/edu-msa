package com.edu.auth.ratelimit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * AttemptStore 선택 — edu.auth.ratelimit.store:
 *   redis  : Redis 분산 카운터(다중 replica 공통 정책) + 인메모리 폴백  ← 운영(K8s) 기본 주입값
 *   memory : 인스턴스-로컬 인메모리(단일 인스턴스 개발·테스트)          ← 앱 기본값
 */
@Configuration
public class AttemptStoreConfig {

    @Bean
    @Primary
    public AttemptStore attemptStore(
            @Value("${edu.auth.ratelimit.store:memory}") String store,
            InMemoryAttemptStore memory,
            StringRedisTemplate redisTemplate,
            MeterRegistry registry) {
        if (!"redis".equalsIgnoreCase(store)) {
            return memory;
        }
        Counter failover = Counter.builder("edu.auth.ratelimit.failover")
                .description("rate-limit Redis 접근 실패로 인메모리 폴백한 횟수")
                .register(registry);
        return new FailoverAttemptStore(new RedisAttemptStore(redisTemplate), memory, failover);
    }
}
