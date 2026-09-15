package com.edu.msa.gitea;

import com.edu.msa.gitea.dto.GiteaDtos.CreateRequest;
import com.edu.msa.gitea.dto.GiteaDtos.StatusResponse;
import com.edu.msa.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gitea 계정 셀프 발급 — 로그인 사용자 본인 계정만 다룬다(대상 식별은 JWT uid).
 * 접근 제어는 SecurityConfig 의 기본 규칙(anyRequest().authenticated())을 따른다.
 */
@RestController
@RequestMapping("/api/gitea/account")
public class GiteaAccountController {

    private final GiteaAccountService service;

    public GiteaAccountController(GiteaAccountService service) {
        this.service = service;
    }

    @GetMapping
    public StatusResponse status(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.status(principal);
    }

    /** 내 Gitea 레포 목록 — 등록 화면 "내 레포에서 선택"용. 미발급이면 빈 목록. */
    @GetMapping("/repos")
    public java.util.List<com.edu.msa.gitea.dto.GiteaDtos.RepoView> repos(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return service.repos(principal);
    }

    @PostMapping
    public ResponseEntity<StatusResponse> create(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @Valid @RequestBody CreateRequest req) {
        StatusResponse created = service.create(principal, req.username(), req.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
