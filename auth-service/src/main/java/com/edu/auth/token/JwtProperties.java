package com.edu.auth.token;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 설정. 시크릿은 소스에 두지 않고 환경변수 EDU_JWT_SECRET 으로 주입한다
 * (application.yml 의 edu.jwt.secret 이 해당 환경변수를 참조).
 */
@ConfigurationProperties(prefix = "edu.jwt")
public class JwtProperties {

    /** HS256 서명 키. 최소 32바이트 이상이어야 한다. */
    private String secret;

    /** 토큰 발급자 — 플랫폼 backend 검증 시에도 동일 값을 사용한다. */
    private String issuer = "edu-auth-service";

    /** Access Token 유효 기간(초). */
    private long accessTtlSeconds = 1800;

    /** Refresh Token 유효 기간(초). */
    private long refreshTtlSeconds = 1209600;

    /**
     * 데모 로그인의 Refresh Token 유효 기간(초).
     * 시연용 세션이 오래 남아 로그인 화면을 확인하기 어려워지지 않도록 짧게 둔다.
     */
    private long demoRefreshTtlSeconds = 86400;

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }

    public long getAccessTtlSeconds() { return accessTtlSeconds; }
    public void setAccessTtlSeconds(long accessTtlSeconds) { this.accessTtlSeconds = accessTtlSeconds; }

    public long getRefreshTtlSeconds() { return refreshTtlSeconds; }
    public void setRefreshTtlSeconds(long refreshTtlSeconds) { this.refreshTtlSeconds = refreshTtlSeconds; }

    public long getDemoRefreshTtlSeconds() { return demoRefreshTtlSeconds; }
    public void setDemoRefreshTtlSeconds(long demoRefreshTtlSeconds) {
        this.demoRefreshTtlSeconds = demoRefreshTtlSeconds;
    }
}
