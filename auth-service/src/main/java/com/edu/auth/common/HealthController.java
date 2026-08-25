package com.edu.auth.common;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class HealthController {

    @GetMapping({"/health", "/healthz"})
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "auth-service");
    }
}
