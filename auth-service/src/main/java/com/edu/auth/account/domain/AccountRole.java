package com.edu.auth.account.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 계정 권한. 저장/JWT 클레임은 대문자 상수명(USER/CODER/ADMIN)을 사용하고,
 * API 응답 JSON은 기존 플랫폼 프론트엔드 계약에 맞춰 소문자 코드를 사용한다.
 */
public enum AccountRole {
    USER("user"), CODER("coder"), ADMIN("admin");

    private final String code;

    AccountRole(String code) { this.code = code; }

    @JsonValue
    public String code() { return code; }

    /** JWT role 클레임 값 — 대문자. */
    public String claim() { return name(); }

    @JsonCreator
    public static AccountRole from(String value) {
        if (value == null) throw new IllegalArgumentException("권한 값이 비어 있습니다.");
        for (AccountRole r : values()) {
            if (r.code.equalsIgnoreCase(value) || r.name().equalsIgnoreCase(value)) return r;
        }
        throw new IllegalArgumentException("알 수 없는 권한: " + value);
    }
}
