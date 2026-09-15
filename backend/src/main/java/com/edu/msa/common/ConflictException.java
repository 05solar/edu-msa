package com.edu.msa.common;

/** 현재 상태와 어긋나는 요청(이미 발급됨·아이디 선점 등) — 409 로 변환된다. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
