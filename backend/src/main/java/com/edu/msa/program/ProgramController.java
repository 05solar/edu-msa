package com.edu.msa.program;

import com.edu.msa.common.Role;
import com.edu.msa.deploy.DeploymentService;
import com.edu.msa.program.dto.ProgramDtos.CommentRequest;
import com.edu.msa.program.dto.ProgramDtos.CommentResponse;
import com.edu.msa.program.dto.ProgramDtos.CreateProgramRequest;
import com.edu.msa.program.dto.ProgramDtos.ProgramDetailResponse;
import com.edu.msa.program.dto.ProgramDtos.ProgramSummaryResponse;
import com.edu.msa.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/programs")
public class ProgramController {

    private final ProgramService service;
    private final DeploymentService deployService;

    public ProgramController(ProgramService service, DeploymentService deployService) {
        this.service = service;
        this.deployService = deployService;
    }

    @GetMapping
    public List<ProgramSummaryResponse> list(
            @RequestParam(required = false) String cat,
            @RequestParam(required = false) List<String> purpose,
            @RequestParam(required = false) List<String> tech,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "latest") String sort) {
        return service.list(cat, purpose, tech, scope, q, sort);
    }

    @GetMapping("/pending")
    public List<ProgramSummaryResponse> pending() {
        return service.pending();
    }

    @GetMapping("/all")
    public List<ProgramSummaryResponse> all() {
        return service.all();
    }

    @GetMapping("/{id}")
    public ProgramDetailResponse detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProgramDetailResponse create(@Valid @RequestBody CreateProgramRequest req) {
        return service.create(req);
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse addComment(@PathVariable Long id, @Valid @RequestBody CommentRequest req) {
        return service.addComment(id, req);
    }

    /**
     * 프로그램 삭제 — 소유자 본인 또는 운영 관리자(ADMIN)만.
     * 권한 검증 후 배포 흔적(컨테이너/K8s 리소스·라우트·배포 기록)을 먼저 정리하고,
     * 프로그램과 부속 데이터(의견·알림)를 삭제한다.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal who) {
        service.requireDeletable(id, who.name(), who.role() == Role.ADMIN);
        deployService.removeFor(id);
        service.delete(id);
    }
}
