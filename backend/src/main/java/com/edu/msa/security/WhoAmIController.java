package com.edu.msa.security;

import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * auth-service 가 발급한 JWT 를 플랫폼 backend 가 스스로 검증했음을 확인하는 엔드포인트.
 * 토큰 클레임만으로 응답하며 auth-service 를 호출하지 않는다.
 */
@RestController
@RequestMapping("/api")
public class WhoAmIController {

    @GetMapping("/whoami")
    public Map<String, Object> whoami(@AuthenticationPrincipal AuthPrincipal principal) {
        return Map.of(
                "id", principal.id(),
                "username", principal.username(),
                "name", principal.name(),
                "dept", principal.dept(),
                "role", principal.role().code(),
                "verifiedBy", "edu-msa-backend");
    }
}
