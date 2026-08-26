package com.edu.auth.session;

import com.edu.auth.account.domain.Account;
import com.edu.auth.account.domain.AccountRole;
import com.edu.auth.account.dto.AccountDtos.AccountResponse;
import com.edu.auth.account.repository.AccountRepository;
import com.edu.auth.common.NotFoundException;
import com.edu.auth.common.UnauthorizedException;
import com.edu.auth.session.domain.RefreshToken;
import com.edu.auth.session.dto.AuthDtos.IssuedTokens;
import com.edu.auth.session.repository.RefreshTokenRepository;
import com.edu.auth.token.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인/토큰 갱신/로그아웃.
 * 로그인 흐름은 ID·PW 확인 → Access Token + Refresh Token 발급 이며,
 * Refresh Token 은 갱신할 때마다 회전(기존 것 폐기 + 새로 발급)시킨다.
 */
@Service
public class AuthService {

    private final AccountRepository accounts;
    private final RefreshTokenRepository refreshTokens;
    private final SessionRevoker sessionRevoker;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwt;
    private final DemoProperties demo;

    public AuthService(AccountRepository accounts, RefreshTokenRepository refreshTokens,
                       SessionRevoker sessionRevoker, PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwt, DemoProperties demo) {
        this.accounts = accounts;
        this.refreshTokens = refreshTokens;
        this.sessionRevoker = sessionRevoker;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.demo = demo;
    }

    @Transactional
    public IssuedTokens login(String username, String rawPassword) {
        // 아이디 존재 여부를 응답으로 구분할 수 없도록 실패 메시지를 통일한다.
        Account account = accounts.findByUsername(username == null ? "" : username.trim())
                .orElseThrow(() -> new UnauthorizedException("아이디 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(rawPassword, account.getPasswordHash())) {
            throw new UnauthorizedException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        return issue(account);
    }

    /**
     * 시연용 데모 로그인 — 비밀번호 없이 역할별 데모 계정의 토큰을 발급한다.
     * 발급되는 토큰은 일반 로그인과 완전히 같으므로 이후 흐름에 차이가 없다.
     */
    @Transactional
    public IssuedTokens demoLogin(AccountRole role) {
        if (!demo.isEnabled()) {
            throw new NotFoundException("데모 로그인이 비활성화되어 있습니다.");
        }
        String username = demo.getAccounts().get(role.code());
        if (username == null || username.isBlank()) {
            throw new NotFoundException("해당 역할의 데모 계정이 설정되어 있지 않습니다: " + role.code());
        }
        Account account = accounts.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("데모 계정을 찾을 수 없습니다: " + username));
        return issue(account, jwt.getDemoRefreshTtlSeconds());
    }

    @Transactional
    public IssuedTokens refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthorizedException("로그인이 필요합니다.");
        }
        Claims claims = jwt.parseExpecting(rawRefreshToken, JwtTokenProvider.TYPE_REFRESH);

        RefreshToken stored = refreshTokens.findByTokenHash(JwtTokenProvider.hash(rawRefreshToken))
                .orElseThrow(() -> new UnauthorizedException("만료되었거나 사용할 수 없는 세션입니다."));

        if (!stored.isUsable()) {
            // 이미 폐기된 토큰이 다시 제출되면 탈취 가능성이 있으므로 해당 계정의 세션을 모두 끊는다.
            // 아래에서 예외를 던지므로 폐기는 별도 트랜잭션으로 커밋시킨다.
            sessionRevoker.revokeAllOf(stored.getAccountId());
            throw new UnauthorizedException("만료되었거나 사용할 수 없는 세션입니다.");
        }

        Account account = accounts.findById(claims.get("uid", Number.class).longValue())
                .orElseThrow(() -> new UnauthorizedException("계정을 찾을 수 없습니다."));

        stored.revoke();
        // 회전 시 원래 세션의 유효 기간을 유지한다.
        // 그러지 않으면 짧게 발급한 데모 세션이 갱신될 때마다 일반 세션 길이로 늘어난다.
        long ttlSeconds = Duration.between(stored.getCreatedAt(), stored.getExpiresAt()).toSeconds();
        return issue(account, ttlSeconds > 0 ? ttlSeconds : jwt.getRefreshTtlSeconds());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) return;
        // 이미 만료·위조된 토큰이어도 로그아웃 자체는 성공으로 처리한다.
        refreshTokens.findByTokenHash(JwtTokenProvider.hash(rawRefreshToken))
                .ifPresent(RefreshToken::revoke);
    }

    private IssuedTokens issue(Account account) {
        return issue(account, jwt.getRefreshTtlSeconds());
    }

    private IssuedTokens issue(Account account, long refreshTtlSeconds) {
        String accessToken = jwt.createAccessToken(account);
        String refreshToken = jwt.createRefreshToken(account, refreshTtlSeconds);

        refreshTokens.save(new RefreshToken(
                account.getId(),
                JwtTokenProvider.hash(refreshToken),
                OffsetDateTime.now().plusSeconds(refreshTtlSeconds)));

        return new IssuedTokens(accessToken, refreshToken,
                jwt.getAccessTtlSeconds(), refreshTtlSeconds,
                AccountResponse.of(account));
    }
}
