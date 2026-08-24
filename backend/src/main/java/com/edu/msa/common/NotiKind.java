package com.edu.msa.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum NotiKind {
    COMMENT("comment"), REJECT("reject"), SUBMIT("submit"), VERSION("version"), APPROVE("approve");

    private final String code;
    NotiKind(String code) { this.code = code; }

    @JsonValue
    public String code() { return code; }

    @JsonCreator
    public static NotiKind from(String code) {
        for (NotiKind k : values()) if (k.code.equalsIgnoreCase(code)) return k;
        throw new IllegalArgumentException("알 수 없는 알림 종류: " + code);
    }
}
