package com.edu.msa.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * auth-service 가 발급한 Access Token 을 자체 검증한다.
 *
 * 인증이 필요한 요청마다 auth-service 를 호출하지 않고, 동일한 EDU_JWT_SECRET 으로
 * 서명을 직접 확인한 뒤 사용자와 역할을 판단한다.
 */
@Component
public class JwtVerifier {

    /** auth-service 와 동일하게 HS256 만 허용한다(알고리즘 다운그레이드 방지). */
    private static final String KEY_ALGORITHM = "HmacSHA256";
    private static final int MIN_SECRET_BYTES = 32;

    private final String secret;
    private final String issuer;
    private SecretKey key;

    public JwtVerifier(@Value("${edu.jwt.secret:}") String secret,
                       @Value("${edu.jwt.issuer}") String issuer) {
        this.secret = secret;
        this.issuer = issuer;
    }

    @PostConstruct
    void init() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT 시크릿이 설정되지 않았습니다. auth-service 와 동일한 EDU_JWT_SECRET 을 지정하세요.");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "EDU_JWT_SECRET 은 최소 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다. (현재 "
                            + bytes.length + "바이트)");
        }
        this.key = new SecretKeySpec(bytes, KEY_ALGORITHM);
    }

    /** 서명·만료·발급자·토큰 종류를 검증하고 클레임을 반환한다. 실패 시 null. */
    public Claims verifyAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return "access".equals(claims.get("typ", String.class)) ? claims : null;
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
