package com.edu.msa.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Role {
    USER("user"), CODER("coder"), ADMIN("admin");

    private final String code;
    Role(String code) { this.code = code; }

    @JsonValue
    public String code() { return code; }

    @JsonCreator
    public static Role from(String code) {
        for (Role r : values()) if (r.code.equalsIgnoreCase(code)) return r;
        throw new IllegalArgumentException("알 수 없는 권한: " + code);
    }
}
