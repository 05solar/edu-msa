package com.edu.msa.deploy;

import com.edu.msa.common.Role;
import com.edu.msa.deploy.dto.DeployDtos.DeployJobResponse;
import com.edu.msa.deploy.dto.DeployDtos.DeployRequest;
import com.edu.msa.deploy.dto.DeployDtos.DeploymentResponse;
import com.edu.msa.deploy.dto.DeployDtos.RedeployRequest;
import com.edu.msa.deploy.dto.DeployDtos.ValidateRequest;
import com.edu.msa.deploy.dto.DeployDtos.ValidationResult;
import com.edu.msa.program.ProgramService;
import com.edu.msa.program.domain.Program;
import com.edu.msa.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DeploymentController {

    private final DeploymentService service;
    private final DeployJobService jobService;
    private final ProgramService programService;

    public DeploymentController(DeploymentService service, DeployJobService jobService,
                                ProgramService programService) {
        this.service = service;
        this.jobService = jobService;
        this.programService = programService;
    }

    /** 등록 화면에서 레포 규격을 사전 검증한다(동기). */
    @PostMapping("/deploy/validate")
    public ValidationResult validate(@Valid @RequestBody ValidateRequest req) {
        return service.validate(req);
    }

    // [P2-3] 프로그램 없는 ad-hoc 배포(POST /api/deploy)는 제거됐다 — 모든 배포는
    // 프로그램 lifecycle(등록→승인→배포→삭제)을 탄다. slug 소유권·중복 방지·
    // K8s ownership/cleanup 이 전부 프로그램 단위이기 때문이다(P0-2/P1-5).

    /** 특정 프로그램 배포 — 작업 큐에 적재(대상 프로그램은 경로 id 가 결정한다). */
    @PostMapping("/programs/{id}/deploy")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DeployJobResponse deployProgram(@PathVariable Long id, @Valid @RequestBody DeployRequest req) {
        return jobService.enqueue(id, req.repoUrl(), req.branch(), req.actor());
    }

    /**
     * 등록자 본인 재배포 — 레포를 갱신한 소유자가 새 버전으로 다시 배포한다.
     * 소유자/상태 검증과 버전 반영은 ProgramService 가 수행하고, 레포 주소는
     * 서버에 저장된 값만 쓰므로 임의 레포 주입이 불가능하다. (ADMIN 은 전체 허용)
     */
    @PostMapping("/programs/{id}/redeploy")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DeployJobResponse redeploy(@PathVariable Long id,
                                      @RequestBody(required = false) RedeployRequest req,
                                      @AuthenticationPrincipal AuthPrincipal who) {
        Program p = programService.requestRedeploy(id, who.id(), who.role() == Role.ADMIN,
                req == null ? null : req.version(), req == null ? null : req.note());
        return jobService.enqueue(p.getId(), p.getRepoUrl(), p.getBranch(), who.name());
    }

    /** 배포 작업 큐 목록. */
    @GetMapping("/deploy/jobs")
    public List<DeployJobResponse> jobs() {
        return jobService.list();
    }

    /** 프로그램의 최근 배포 상태. */
    @GetMapping("/programs/{id}/deployment")
    public DeploymentResponse latest(@PathVariable Long id) {
        return service.latest(id);
    }
}
