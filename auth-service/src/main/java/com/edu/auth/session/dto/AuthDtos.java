package com.edu.auth.session.dto;

import com.edu.auth.account.dto.AccountDtos.AccountResponse;
import jakarta.validation.constraints.NotBlank;

/** 로그인/토큰 갱신 요청·응답. Refresh Token 은 본문이 아니라 HttpOnly 쿠키로만 오간다. */
public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(
            @NotBlank(message = "아이디를 입력해 주세요.") String username,
            @NotBlank(message = "비밀번호를 입력해 주세요.") String password
    ) {}

    /** 시연용 데모 로그인 — 역할만 지정한다. */
    public record DemoLoginRequest(
            @NotBlank(message = "역할을 입력해 주세요.") String role
    ) {}

    public record TokenResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            AccountResponse account
    ) {
        public static TokenResponse of(String accessToken, long expiresIn, AccountResponse account) {
            return new TokenResponse(accessToken, "Bearer", expiresIn, account);
        }
    }

    /** 발급된 토큰 한 쌍 — Refresh 원문은 쿠키로만 내보내고 응답 본문에는 넣지 않는다. */
    public record IssuedTokens(String accessToken, String refreshToken, long accessTtlSeconds,
                               long refreshTtlSeconds, AccountResponse account) {}
}
