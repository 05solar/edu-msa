package com.edu.msa.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * 카탈로그 캐시 즉시 무효화 — 프로그램 생성/삭제/상태 전이/배포 공개 등
 * 목록에 영향을 주는 변경 지점에서 호출한다. TTL(30~60s)이 상한을 이루므로
 * 여기서 놓쳐도 stale 이 장기간 유지되지는 않는다.
 * (프록시를 거치지 않는 내부 호출 지점에서도 쓸 수 있도록 애노테이션 대신 직접 clear 한다)
 */
@Component
public class CatalogCacheEvictor {

    private static final Logger log = LoggerFactory.getLogger(CatalogCacheEvictor.class);

    private final CacheManager cacheManager;

    public CatalogCacheEvictor(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evictAll() {
        clear(CacheConfig.CATALOG_LIST);
        clear(CacheConfig.CATALOG_COUNTS);
    }

    private void clear(String name) {
        try {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        } catch (Exception e) {
            // 캐시 장애가 쓰기 흐름(등록/검토/배포)을 실패시키면 안 된다 — TTL 이 무효화를 대신한다.
            log.warn("카탈로그 캐시 무효화 실패({}) — TTL 만료로 대체된다: {}", name, e.getMessage());
        }
    }
}
