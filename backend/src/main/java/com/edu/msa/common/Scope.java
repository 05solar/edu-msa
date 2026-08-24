package com.edu.msa.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Scope {
    ALL("all"), DEPT("dept");

    private final String code;
    Scope(String code) { this.code = code; }

    @JsonValue
    public String code() { return code; }

    @JsonCreator
    public static Scope from(String code) {
        for (Scope s : values()) if (s.code.equalsIgnoreCase(code)) return s;
        throw new IllegalArgumentException("알 수 없는 공개 범위: " + code);
    }
}
