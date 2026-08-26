package com.edu.auth.common;

/** 대상 자원 없음. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
}
