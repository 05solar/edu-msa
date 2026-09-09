package com.edu.msa.cache;

import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * 캐시(Redis) 장애를 서비스 장애로 번지지 않게 하는 오류 핸들러.
 * get/put/evict 실패를 전부 삼키고 호출부는 DB 경로로 정상 진행한다(캐시 미스와 동일).
 * 로그는 1분에 1회로 제한해 장애 중 로그 폭주를 막는다.
 */
public class LenientCacheErrorHandler implements CacheErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(LenientCacheErrorHandler.class);
    private static final long LOG_INTERVAL_MS = 60_000;

    private final AtomicLong lastLogAt = new AtomicLong(0);

    @Override
    public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
        warn("get", cache, e);
    }

    @Override
    public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
        warn("put", cache, e);
    }

    @Override
    public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
        warn("evict", cache, e);
    }

    @Override
    public void handleCacheClearError(RuntimeException e, Cache cache) {
        warn("clear", cache, e);
    }

    private void warn(String op, Cache cache, RuntimeException e) {
        long now = System.currentTimeMillis();
        long last = lastLogAt.get();
        if (now - last >= LOG_INTERVAL_MS && lastLogAt.compareAndSet(last, now)) {
            log.warn("캐시 {} 실패({}) — DB 폴백으로 계속 서비스한다: {}", op, cache.getName(), e.getMessage());
        }
    }
}
