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

    /**
     * 폐기 토큰 재제출을 "동시 회전 경쟁 패배(정상)"로 볼 시간 창(초).
     * 이 창 안의 재제출은 401 만 반환하고, 그보다 오래된 재사용은 탈취 의심으로
     * 계정 전 세션을 폐기한다. 창 안의 공격 재사용도 어차피 401 로 실패한다 —
     * 놓치는 것은 징벌적 전체 폐기뿐이다(승자 세션 오폐기 방지와의 트레이드오프).
     */
    @org.springframework.beans.factory.annotation.Value("${edu.auth.reuse-grace-seconds:30}")
    private long reuseGraceSeconds;

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

    /**
     * 의도적으로 트랜잭션을 걸지 않는다 — bcrypt 검증(~50ms+)이 트랜잭션 안에 있으면
     * 그 시간 동안 DB 커넥션을 점유해 로그인 폭주 시 풀이 고갈된다(부하 실측으로 확인).
     * 계정 조회는 리포지토리의 짧은 읽기 트랜잭션, 토큰 저장은 issue() 안 save() 의
     * 짧은 쓰기 트랜잭션으로 각각 분리되고, 이 경로는 엔티티를 변경하지 않으므로
     * 묶음 트랜잭션이 필요 없다.
     */
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

    /**
     * 회전(rotation)의 불변식: 동일 refresh token 은 정확히 한 번만 성공한다(P1-3).
     *
     * 과거의 조회→isUsable→revoke 는 읽기·검사·쓰기가 분리돼 동시 요청이 전부
     * 통과했다(실측: 동일 토큰 10요청 → 세션 10개 발급). 지금은 DB 조건부 UPDATE
     * (consumeIfUsable)가 소비를 원자적으로 판정한다 — replica 수와 무관.
     *
     * 폐기된 토큰 재제출의 구분(revoked_at 기준):
     *  · 방금(reuse-grace 이내) 폐기됨 → 동시 회전 경쟁 패배(브라우저 다중 탭 등
     *    정상 시나리오) — 401 만 반환하고 승자의 새 세션은 유지한다.
     *  · 그보다 오래됐거나 시각 불명 → 탈취 의심 — 계정 전 세션 폐기(기존 정책).
     *  · 만료(expires_at 경과)만으로는 전 세션을 끊지 않는다 — 방치된 탭의 만료
     *    쿠키 제출은 공격 신호가 아니다(과거엔 이 경우도 전 세션이 끊겼다 — 정련).
     */
    @Transactional
    public IssuedTokens refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthorizedException("로그인이 필요합니다.");
        }
        Claims claims = jwt.parseExpecting(rawRefreshToken, JwtTokenProvider.TYPE_REFRESH);

        RefreshToken stored = refreshTokens.findByTokenHash(JwtTokenProvider.hash(rawRefreshToken))
                .orElseThrow(() -> new UnauthorizedException("만료되었거나 사용할 수 없는 세션입니다."));

        OffsetDateTime now = OffsetDateTime.now();
        if (stored.getExpiresAt().isBefore(now)) {
            throw new UnauthorizedException("만료되었거나 사용할 수 없는 세션입니다.");
        }

        // 회전 시 원래 세션의 유효 기간을 유지한다(소비 전에 읽는다 — 아래 UPDATE 가
        // 영속성 컨텍스트를 비운다). 짧게 발급한 데모 세션이 갱신될 때마다 일반 세션
        // 길이로 늘어나는 것을 막는다.
        long ttlSeconds = Duration.between(stored.getCreatedAt(), stored.getExpiresAt()).toSeconds();
        Long accountId = stored.getAccountId();
        Long tokenId = stored.getId();

        if (refreshTokens.consumeIfUsable(tokenId, now) == 1) {
            // 승자 — 이 요청만 회전에 성공한다.
            Account account = accounts.findById(claims.get("uid", Number.class).longValue())
                    .orElseThrow(() -> new UnauthorizedException("계정을 찾을 수 없습니다."));
            if (!account.getId().equals(accountId)) {
                throw new UnauthorizedException("만료되었거나 사용할 수 없는 세션입니다.");
            }
            return issue(account, ttlSeconds > 0 ? ttlSeconds : jwt.getRefreshTtlSeconds());
        }

        // 소비 실패 = 이미 폐기된 토큰. 방금 폐기(동시 경쟁 패배)인지 오래된 재사용
        // (탈취 의심)인지 revoked_at 으로 구분한다(컨텍스트가 비워졌으므로 재조회).
        OffsetDateTime revokedAt = refreshTokens.findById(tokenId)
                .map(RefreshToken::getRevokedAt).orElse(null);
        if (revokedAt != null && revokedAt.isAfter(now.minusSeconds(reuseGraceSeconds))) {
            throw new UnauthorizedException("만료되었거나 사용할 수 없는 세션입니다.");
        }
        // 탈취 의심 — 예외로 롤백되지 않도록 전 세션 폐기는 별도 트랜잭션으로 커밋한다.
        sessionRevoker.revokeAllOf(accountId);
        throw new UnauthorizedException("만료되었거나 사용할 수 없는 세션입니다.");
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
