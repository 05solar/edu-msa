package com.edu.msa.cache;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/**
 * 공개 카탈로그 읽기 캐시 설정.
 *
 * - 캐시: catalogList(목록 페이지 응답) · catalogCounts(분야별 개수) — TTL 은 환경변수로 조정.
 * - 저장소: Redis(spring.cache.type=redis, 기본). 테스트는 none/simple 로 대체한다.
 * - 장애 폴백: LenientCacheErrorHandler 가 Redis 오류를 전부 삼켜 DB 경로로 정상 서빙.
 * - 무효화: TTL(상한) + 데이터 변경 지점의 CatalogCacheEvictor 즉시 무효화(이중).
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String CATALOG_LIST = "catalogList";
    public static final String CATALOG_COUNTS = "catalogCounts";

    @Value("${edu.cache.list-ttl-seconds:30}")
    private long listTtlSeconds;

    @Value("${edu.cache.counts-ttl-seconds:60}")
    private long countsTtlSeconds;

    @Override
    public CacheErrorHandler errorHandler() {
        return new LenientCacheErrorHandler();
    }

    /** Redis 캐시 매니저 사용 시 캐시별 TTL·직렬화(JSON) 지정. simple/none 캐시에서는 무시된다. */
    @Bean
    public RedisCacheManagerBuilderCustomizer eduCacheTtls() {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .prefixCacheNameWith("edu:cache:");
        return builder -> builder
                .withCacheConfiguration(CATALOG_LIST, base.entryTtl(Duration.ofSeconds(listTtlSeconds)))
                .withCacheConfiguration(CATALOG_COUNTS, base.entryTtl(Duration.ofSeconds(countsTtlSeconds)));
    }
}
