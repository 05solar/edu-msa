package com.edu.auth.token;

import com.edu.auth.account.domain.Account;
import com.edu.auth.common.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.MacAlgorithm;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * HS256 JWT 발급/검증.
 * Access Token 은 자원 서버(플랫폼 backend)가 auth-service 호출 없이 직접 검증할 수 있도록
 * 사용자 식별 정보와 role 클레임을 모두 담는다.
 */
@Component
public class JwtTokenProvider {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    /** HS256 고정. 키 길이에 따라 알고리즘이 자동으로 올라가지 않도록 명시한다. */
    private static final MacAlgorithm ALGORITHM = Jwts.SIG.HS256;
    private static final String KEY_ALGORITHM = "HmacSHA256";
    private static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties props;
    private SecretKey key;

    public JwtTokenProvider(JwtProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        String secret = props.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT 시크릿이 설정되지 않았습니다. 환경변수 EDU_JWT_SECRET 을 지정하세요.");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "EDU_JWT_SECRET 은 최소 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다. (현재 "
                            + bytes.length + "바이트)");
        }
        this.key = new SecretKeySpec(bytes, KEY_ALGORITHM);
    }

    public String createAccessToken(Account account) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(props.getIssuer())
                .subject(account.getUsername())
                .claim("uid", account.getId())
                .claim("role", account.getRole().claim())
                .claim("name", account.getName())
                .claim("dept", account.getDept())
                .claim("typ", TYPE_ACCESS)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(props.getAccessTtlSeconds())))
                .signWith(key, ALGORITHM)
                .compact();
    }

    public String createRefreshToken(Account account) {
        return createRefreshToken(account, props.getRefreshTtlSeconds());
    }

    public String createRefreshToken(Account account, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(props.getIssuer())
                .subject(account.getUsername())
                .claim("uid", account.getId())
                .claim("typ", TYPE_REFRESH)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key, ALGORITHM)
                .compact();
    }

    /** 서명·만료·발급자를 검증하고 클레임을 반환한다. */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(props.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException("유효하지 않은 토큰입니다.");
        }
    }

    public Claims parseExpecting(String token, String type) {
        Claims claims = parse(token);
        if (!type.equals(claims.get("typ", String.class))) {
            throw new UnauthorizedException("토큰 종류가 올바르지 않습니다.");
        }
        return claims;
    }

    public long getAccessTtlSeconds() { return props.getAccessTtlSeconds(); }

    public long getRefreshTtlSeconds() { return props.getRefreshTtlSeconds(); }

    public long getDemoRefreshTtlSeconds() { return props.getDemoRefreshTtlSeconds(); }

    /** Refresh Token 은 원문 대신 해시로 저장한다. */
    public static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("토큰 해시 생성에 실패했습니다.", e);
        }
    }
}
