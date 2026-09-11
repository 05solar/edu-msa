package com.edu.msa.deploy;

/**
 * 배포 파이프라인 오류. permanent=true 는 재시도해도 해결되지 않는 영구 오류
 * (규격 검증 실패·slug 예약 충돌 등)로, 재시도 큐에 다시 들어가면 안 된다.
 * 일시 오류(네트워크·레지스트리 타임아웃 등)는 기본값(false)으로 백오프 재시도된다.
 */
public class DeployException extends RuntimeException {

    private final boolean permanent;

    public DeployException(String message) {
        this(message, false);
    }

    private DeployException(String message, boolean permanent) {
        super(message);
        this.permanent = permanent;
    }

    public static DeployException permanent(String message) {
        return new DeployException(message, true);
    }

    public boolean isPermanent() { return permanent; }
}
