package com.edu.auth.common;

/** 자격 증명 실패 · 토큰 만료/위조. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) { super(message); }
}
