package com.edu.auth.common;

/** 아이디/이메일 중복 등 자원 충돌. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}
