package com.edu.auth.token;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Refresh Token 전달용 쿠키. JavaScript 접근을 막기 위해 HttpOnly 로 내려보내고,
 * 경로를 /api/auth 로 제한해 다른 서비스 요청에는 실리지 않게 한다.
 *
 * Secure/SameSite 는 배포 환경에 따라 달라지므로 환경변수로 주입한다.
 * (로컬 http 개발에서는 Secure=false, HTTPS 배포에서는 true)
 */
@Component
public class RefreshCookies {

    public static final String NAME = "edu_refresh";
    private static final String PATH = "/api/auth";

    private final boolean secure;
    private final String sameSite;

    public RefreshCookies(
            @Value("${edu.auth.cookie.secure}") boolean secure,
            @Value("${edu.auth.cookie.same-site}") String sameSite) {
        this.secure = secure;
        this.sameSite = sameSite;
    }

    public ResponseCookie issue(String token, long maxAgeSeconds) {
        return base(token).maxAge(Duration.ofSeconds(maxAgeSeconds)).build();
    }

    /** 로그아웃 — 같은 속성으로 즉시 만료시켜야 브라우저가 삭제한다. */
    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(PATH);
    }

    public static Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        return Arrays.stream(cookies)
                .filter(c -> NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst();
    }
}
