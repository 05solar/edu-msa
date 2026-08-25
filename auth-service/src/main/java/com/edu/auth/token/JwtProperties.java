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

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }

    public long getAccessTtlSeconds() { return accessTtlSeconds; }
    public void setAccessTtlSeconds(long accessTtlSeconds) { this.accessTtlSeconds = accessTtlSeconds; }

    public long getRefreshTtlSeconds() { return refreshTtlSeconds; }
    public void setRefreshTtlSeconds(long refreshTtlSeconds) { this.refreshTtlSeconds = refreshTtlSeconds; }
}
