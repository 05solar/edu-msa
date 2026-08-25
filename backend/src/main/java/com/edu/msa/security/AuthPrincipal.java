package com.edu.msa.security;

import com.edu.msa.common.Role;
import io.jsonwebtoken.Claims;

/**
 * JWT 클레임에서 복원한 요청자 정보.
 * auth-service 의 role 클레임은 대문자(USER/CODER/ADMIN)이고,
 * 플랫폼 backend 의 Role 은 소문자 코드를 쓰므로 Role.from 이 양쪽을 흡수한다.
 */
public record AuthPrincipal(Long id, String username, String name, String dept, Role role) {

    public static AuthPrincipal from(Claims claims) {
        Number uid = claims.get("uid", Number.class);
        return new AuthPrincipal(
                uid == null ? null : uid.longValue(),
                claims.getSubject(),
                claims.get("name", String.class),
                claims.get("dept", String.class),
                Role.from(claims.get("role", String.class)));
    }

    /** Spring Security 권한 표기 — hasRole('ADMIN') 형태로 검사한다. */
    public String authority() { return "ROLE_" + role.name(); }
}
