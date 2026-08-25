package com.edu.auth.security;

import com.edu.auth.account.domain.AccountRole;
import io.jsonwebtoken.Claims;

/** Access Token 클레임에서 복원한 인증 주체. DB 조회 없이 토큰만으로 구성한다. */
public record AuthPrincipal(Long id, String username, AccountRole role, String name, String dept) {

    public static AuthPrincipal from(Claims claims) {
        Number uid = claims.get("uid", Number.class);
        return new AuthPrincipal(
                uid == null ? null : uid.longValue(),
                claims.getSubject(),
                AccountRole.from(claims.get("role", String.class)),
                claims.get("name", String.class),
                claims.get("dept", String.class));
    }

    /** Spring Security 권한 표기 — hasRole('ADMIN') 형태로 검사한다. */
    public String authority() { return "ROLE_" + role.claim(); }
}
