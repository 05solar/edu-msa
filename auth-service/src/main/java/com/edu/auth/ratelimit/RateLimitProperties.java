package com.edu.auth.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 로그인/토큰 갱신 남용 방어 정책값.
 * 정상 사용자 과잉 잠금을 피하도록 계정 기준은 짧은 임시 차단,
 * IP 기준은 공용망(NAT)을 고려해 더 관대한 임계값을 기본으로 둔다.
 * 모든 값은 환경변수로 재정의할 수 있다(application.yml 참조).
 */
@ConfigurationProperties(prefix = "edu.auth.ratelimit")
public class RateLimitProperties {

    /** 전체 방어 스위치(테스트·문제 시 우회용). */
    private boolean enabled = true;

    /** 같은 계정 기준: window 초 안에 maxFailures 초과 실패 → blockSeconds 임시 차단. */
    private int accountMaxFailures = 5;
    private long accountWindowSeconds = 600;
    private long accountBlockSeconds = 300;

    /** 같은 IP 기준(모든 계정 합산): 크리덴셜 스터핑 방어. NAT 고려해 계정 기준보다 관대하게. */
    private int ipMaxFailures = 30;
    private long ipWindowSeconds = 600;
    private long ipBlockSeconds = 600;

    /** 실패 응답 지연(타르핏): baseDelay × 2^(실패횟수-1), 상한 maxDelay. 0 이면 지연 없음. */
    private long failDelayMs = 300;
    private long maxFailDelayMs = 2000;

    /** refresh 남용(위조·만료 토큰 반복 제출) 기준 — IP 단위. */
    private int refreshMaxFailures = 30;
    private long refreshWindowSeconds = 60;
    private long refreshBlockSeconds = 300;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getAccountMaxFailures() { return accountMaxFailures; }
    public void setAccountMaxFailures(int accountMaxFailures) { this.accountMaxFailures = accountMaxFailures; }
    public long getAccountWindowSeconds() { return accountWindowSeconds; }
    public void setAccountWindowSeconds(long accountWindowSeconds) { this.accountWindowSeconds = accountWindowSeconds; }
    public long getAccountBlockSeconds() { return accountBlockSeconds; }
    public void setAccountBlockSeconds(long accountBlockSeconds) { this.accountBlockSeconds = accountBlockSeconds; }
    public int getIpMaxFailures() { return ipMaxFailures; }
    public void setIpMaxFailures(int ipMaxFailures) { this.ipMaxFailures = ipMaxFailures; }
    public long getIpWindowSeconds() { return ipWindowSeconds; }
    public void setIpWindowSeconds(long ipWindowSeconds) { this.ipWindowSeconds = ipWindowSeconds; }
    public long getIpBlockSeconds() { return ipBlockSeconds; }
    public void setIpBlockSeconds(long ipBlockSeconds) { this.ipBlockSeconds = ipBlockSeconds; }
    public long getFailDelayMs() { return failDelayMs; }
    public void setFailDelayMs(long failDelayMs) { this.failDelayMs = failDelayMs; }
    public long getMaxFailDelayMs() { return maxFailDelayMs; }
    public void setMaxFailDelayMs(long maxFailDelayMs) { this.maxFailDelayMs = maxFailDelayMs; }
    public int getRefreshMaxFailures() { return refreshMaxFailures; }
    public void setRefreshMaxFailures(int refreshMaxFailures) { this.refreshMaxFailures = refreshMaxFailures; }
    public long getRefreshWindowSeconds() { return refreshWindowSeconds; }
    public void setRefreshWindowSeconds(long refreshWindowSeconds) { this.refreshWindowSeconds = refreshWindowSeconds; }
    public long getRefreshBlockSeconds() { return refreshBlockSeconds; }
    public void setRefreshBlockSeconds(long refreshBlockSeconds) { this.refreshBlockSeconds = refreshBlockSeconds; }
}
