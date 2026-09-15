package com.edu.msa.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "Not Found", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .orElse("입력값이 올바르지 않습니다.");
        return ResponseEntity.badRequest().body(ApiError.of(400, "Bad Request", msg));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiError.of(400, "Bad Request", ex.getMessage()));
    }

    /** 상태 충돌(이미 발급됨·아이디 선점 등) — 요청 자체는 유효하나 현재 상태와 어긋남. */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "Conflict", ex.getMessage()));
    }

    /**
     * 서버 내부 상태 오류(외부 연동 실패 등) — 500 을 유지하되 사용자 안내 메시지를 담아 준다.
     * (기동 시 fail-fast 용 IllegalStateException 은 요청 경로에 도달하지 않으므로 무관)
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(500, "Internal Server Error", ex.getMessage()));
    }

    @ExceptionHandler(com.edu.msa.deploy.DeployException.class)
    public ResponseEntity<ApiError> handleDeploy(com.edu.msa.deploy.DeployException ex) {
        return ResponseEntity.badRequest().body(ApiError.of(400, "Deploy Error", ex.getMessage()));
    }

    /**
     * P0-2 동시성 제약 위반 — 동시 요청 중 DB 유니크(부분 인덱스·PK)에서 진 쪽을
     * 500 이 아니라 409 로 변환한다. 그 외 무결성 위반은 기존 처리(500)로 흘린다.
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(org.springframework.dao.DataIntegrityViolationException ex)
            throws org.springframework.dao.DataIntegrityViolationException {
        String cause = String.valueOf(ex.getMostSpecificCause());
        if (cause.contains("uq_deploy_jobs_active_program")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiError.of(409, "Conflict", "이미 이 프로그램의 배포가 진행 중입니다."));
        }
        if (cause.contains("slug_claims")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiError.of(409, "Conflict", "slug 가 이미 다른 서비스에 사용 중입니다."));
        }
        if (cause.contains("uq_gitea_accounts")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiError.of(409, "Conflict", "이미 발급되었거나 사용 중인 Gitea 아이디입니다."));
        }
        throw ex;
    }
}
