package com.edu.auth.session;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * refresh_tokens 정리 정책.
 *
 * 삭제 대상은 "만료 시각 + 보존 기간(retention)"이 지난 행뿐이다.
 * - 만료된 토큰은 JWT 파싱 단계에서 이미 거부되므로 인증에 쓰이지 않고,
 * - 폐기(revoked)됐지만 아직 만료 전인 행은 탈취 감지(재사용 시 전체 세션 무효화)에
 *   필요하므로 만료될 때까지 지우지 않는다.
 * 보존 기간은 만료 직후의 감사/디버깅 여유분이다.
 */
@ConfigurationProperties(prefix = "edu.auth.cleanup")
public class CleanupProperties {

    /** 정리 스케줄러 동작 여부. */
    private boolean enabled = true;

    /** 실행 주기(ms). 기본 1시간. */
    private long intervalMs = 3_600_000;

    /** 기동 후 첫 실행까지 지연(ms) — 재시작 직후 부하·다중 replica 동시 실행을 완화. */
    private long initialDelayMs = 60_000;

    /** 만료 후 보존 기간(시간). 이 기간이 지난 행만 삭제한다. */
    private long retentionHours = 24;

    /** 1회 삭제 배치 크기 — 대량 백로그에서 긴 잠금·긴 트랜잭션을 피한다. */
    private int batchSize = 10_000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getIntervalMs() { return intervalMs; }
    public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }
    public long getInitialDelayMs() { return initialDelayMs; }
    public void setInitialDelayMs(long initialDelayMs) { this.initialDelayMs = initialDelayMs; }
    public long getRetentionHours() { return retentionHours; }
    public void setRetentionHours(long retentionHours) { this.retentionHours = retentionHours; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
}
