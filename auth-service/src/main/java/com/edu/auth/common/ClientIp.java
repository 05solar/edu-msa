package com.edu.auth.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 클라이언트 IP 추출 — 프록시(ingress-nginx/Traefik) 뒤에서는 X-Forwarded-For 의
 * 첫 값을 쓰고, 없으면 소켓 원격 주소를 쓴다.
 * (엣지에서 XFF 를 신뢰할 수 있게 설정하는 전제 — 위조 가능성이 있어도 계정 기준
 *  제한이 함께 걸리므로 brute force 자체는 막힌다.)
 */
public final class ClientIp {

    private ClientIp() {}

    public static String from(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String addr = request.getRemoteAddr();
        return addr == null ? "unknown" : addr;
    }
}
