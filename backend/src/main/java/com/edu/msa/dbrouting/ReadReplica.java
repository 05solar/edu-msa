package com.edu.msa.dbrouting;

import java.util.function.Supplier;

/**
 * read replica 라우팅 마커.
 *
 * 라우팅 규칙(ReadRoutingDataSourceConfig): "현재 트랜잭션이 readOnly" AND "이 마커가 켜짐"
 * 일 때만 replica 로 간다 — readOnly 가 아니면 마커가 켜져 있어도 절대 replica 로 가지 않는다
 * (쓰기 안전), 반대로 readOnly 라도 마커가 없으면 primary 로 간다(read-after-write 일관성 기본).
 *
 * replica 허용 기준: 즉시 일관성이 필요 없는 read-heavy 조회만 명시적으로 선정한다.
 * (복제 지연 수십~수백 ms 를 허용할 수 있는 화면 — 카탈로그 목록/집계처럼
 *  이미 30~60s TTL 캐시로 stale 을 수용하는 경로가 대표)
 */
public final class ReadReplica {

    private static final ThreadLocal<Boolean> MARK = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private ReadReplica() {}

    public static boolean isMarked() {
        return MARK.get();
    }

    /** 블록 안의 DB 접근을 replica 후보로 마킹한다(readOnly 트랜잭션일 때만 실제 라우팅). */
    public static <T> T route(Supplier<T> work) {
        MARK.set(Boolean.TRUE);
        try {
            return work.get();
        } finally {
            MARK.remove();
        }
    }
}
