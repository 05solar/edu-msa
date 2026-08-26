package com.edu.auth.session;

import com.edu.auth.account.domain.AccountRole;
import com.edu.auth.session.dto.AuthDtos.DemoLoginRequest;
import com.edu.auth.session.dto.AuthDtos.IssuedTokens;
import com.edu.auth.session.dto.AuthDtos.LoginRequest;
import com.edu.auth.session.dto.AuthDtos.TokenResponse;
import com.edu.auth.token.RefreshCookies;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인 세션 엔드포인트.
 * Access Token 은 응답 본문으로, Refresh Token 은 HttpOnly 쿠키로만 내보낸다.
 */
@RestController
@RequestMapping("/api/auth")
public class SessionController {

    private final AuthService authService;
    private final RefreshCookies cookies;

    public SessionController(AuthService authService, RefreshCookies cookies) {
        this.authService = authService;
        this.cookies = cookies;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
        return withRefreshCookie(authService.login(req.username(), req.password()));
    }

    /** 시연용 — 비밀번호 없이 역할별 데모 계정으로 로그인한다. */
    @PostMapping("/demo-login")
    public ResponseEntity<TokenResponse> demoLogin(@Valid @RequestBody DemoLoginRequest req) {
        return withRefreshCookie(authService.demoLogin(AccountRole.from(req.role())));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(HttpServletRequest request) {
        String raw = RefreshCookies.read(request).orElse(null);
        return withRefreshCookie(authService.refresh(raw));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        RefreshCookies.read(request).ifPresent(authService::logout);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .body(Map.of("message", "로그아웃되었습니다."));
    }

    private ResponseEntity<TokenResponse> withRefreshCookie(IssuedTokens tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookies.issue(tokens.refreshToken(), tokens.refreshTtlSeconds()).toString())
                .body(TokenResponse.of(tokens.accessToken(), tokens.accessTtlSeconds(), tokens.account()));
    }
}
