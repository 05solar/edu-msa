package com.edu.auth.ratelimit;

/**
 * 로그인/갱신 실패 시도 카운터 저장소 추상화.
 *
 * 지금은 단일 인스턴스용 인메모리 구현(InMemoryAttemptStore)을 쓰지만,
 * 다중 인스턴스 확장 시 같은 계약으로 Redis 구현(INCR+EXPIRE / SET NX EX)으로
 * 교체할 수 있도록 연산을 Redis 원자 연산에 1:1 대응하는 형태로 잡았다.
 */
public interface AttemptStore {

    /**
     * key 의 실패 카운트를 1 올리고 증가 후 값을 반환한다.
     * 카운터가 처음 만들어질 때 windowSeconds 뒤 만료되도록 설정한다(고정 윈도우).
     */
    long increment(String key, long windowSeconds);

    /** key 를 blockSeconds 동안 차단 상태로 표시한다. */
    void block(String key, long blockSeconds);

    /** 남은 차단 시간(초). 차단 상태가 아니면 0. */
    long blockedRemainingSeconds(String key);

    /** 성공 시 실패 카운터를 지운다(차단 표시는 유지 — 만료로만 풀린다). */
    void reset(String key);
}
