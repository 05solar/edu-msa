package com.edu.auth.common;

import java.time.OffsetDateTime;

public record ApiError(String code, String message, OffsetDateTime timestamp) {
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, OffsetDateTime.now());
    }
}
