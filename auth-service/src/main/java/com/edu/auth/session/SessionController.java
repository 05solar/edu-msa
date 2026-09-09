package com.edu.auth.session;

import com.edu.auth.account.domain.AccountRole;
import com.edu.auth.common.ClientIp;
import com.edu.auth.common.UnauthorizedException;
import com.edu.auth.ratelimit.LoginGuard;
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
 * 남용 방어(LoginGuard)는 트랜잭션(AuthService) 밖인 이 계층에서 적용한다 —
 * 실패 지연(sleep)이 DB 커넥션을 잡지 않게 하기 위함이다.
 */
@RestController
@RequestMapping("/api/auth")
public class SessionController {

    private final AuthService authService;
    private final RefreshCookies cookies;
    private final LoginGuard guard;

    public SessionController(AuthService authService, RefreshCookies cookies, LoginGuard guard) {
        this.authService = authService;
        this.cookies = cookies;
        this.guard = guard;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest req,
                                               HttpServletRequest http) {
        String ip = ClientIp.from(http);
        guard.checkLogin(ip, req.username());
        IssuedTokens tokens;
        try {
            tokens = authService.login(req.username(), req.password());
        } catch (UnauthorizedException e) {
            guard.onLoginFailure(ip, req.username());   // 카운트·차단 등록·응답 지연
            throw e;
        }
        guard.onLoginSuccess(ip, req.username());
        return withRefreshCookie(tokens);
    }

    /** 시연용 — 비밀번호 없이 역할별 데모 계정으로 로그인한다. (기본 비활성 — 명시적으로 켠 환경 전용) */
    @PostMapping("/demo-login")
    public ResponseEntity<TokenResponse> demoLogin(@Valid @RequestBody DemoLoginRequest req) {
        return withRefreshCookie(authService.demoLogin(AccountRole.from(req.role())));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(HttpServletRequest request) {
        String ip = ClientIp.from(request);
        guard.checkRefresh(ip);
        String raw = RefreshCookies.read(request).orElse(null);
        try {
            return withRefreshCookie(authService.refresh(raw));
        } catch (UnauthorizedException e) {
            guard.onRefreshFailure(ip);   // 위조·만료·폐기 토큰 반복 제출 카운트
            throw e;
        }
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
