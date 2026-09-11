package com.edu.msa.program;

import com.edu.msa.common.PageResponse;
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
import java.util.Map;
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
    public PageResponse<ProgramSummaryResponse> list(
            @RequestParam(required = false) String cat,
            @RequestParam(required = false) List<String> purpose,
            @RequestParam(required = false) List<String> tech,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "latest") String sort,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return service.list(cat, purpose, tech, scope, q, sort, page, size);
    }

    /** 카탈로그 사이드바 분야별 공개 프로그램 개수(DB GROUP BY). */
    @GetMapping("/counts")
    public Map<String, Long> counts() {
        return service.publicCountsByCat();
    }

    @GetMapping("/pending")
    public PageResponse<ProgramSummaryResponse> pending(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return service.pending(page, size);
    }

    @GetMapping("/all")
    public PageResponse<ProgramSummaryResponse> all(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "100") int size) {
        return service.all(page, size);
    }

    @GetMapping("/{id}")
    public ProgramDetailResponse detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProgramDetailResponse create(@Valid @RequestBody CreateProgramRequest req,
                                        @AuthenticationPrincipal AuthPrincipal who) {
        // 소유자는 서버가 JWT 로 결정한다 — 요청 본문의 owner 는 무시된다(P1-5)
        return service.create(req, who);
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
        service.requireDeletable(id, who.id(), who.role() == Role.ADMIN);
        deployService.removeFor(id);
        service.delete(id);
    }
}
