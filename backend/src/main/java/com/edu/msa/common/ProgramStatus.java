package com.edu.msa.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** 프론트엔드와 동일한 소문자 토큰으로 직렬화된다. */
public enum ProgramStatus {
    DRAFT("draft"), PENDING("pending"), PUBLIC("public"), REJECTED("rejected"), STOPPED("stopped");

    private final String code;
    ProgramStatus(String code) { this.code = code; }

    @JsonValue
    public String code() { return code; }

    @JsonCreator
    public static ProgramStatus from(String code) {
        for (ProgramStatus s : values()) if (s.code.equalsIgnoreCase(code)) return s;
        throw new IllegalArgumentException("알 수 없는 상태: " + code);
    }
}
