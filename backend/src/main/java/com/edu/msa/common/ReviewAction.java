package com.edu.msa.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ReviewAction {
    APPROVE("approve"), REJECT("reject"), STOP("stop"), RESUME("resume");

    private final String code;
    ReviewAction(String code) { this.code = code; }

    @JsonValue
    public String code() { return code; }

    @JsonCreator
    public static ReviewAction from(String code) {
        for (ReviewAction a : values()) if (a.code.equalsIgnoreCase(code)) return a;
        throw new IllegalArgumentException("알 수 없는 처리: " + code);
    }
}
