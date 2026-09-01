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

    /** 임의 레포 배포 — 작업 큐에 적재하고 즉시 반환(워커가 비동기 처리). */
    @PostMapping("/deploy")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DeployJobResponse deploy(@Valid @RequestBody DeployRequest req) {
        return jobService.enqueue(req.programId(), req.repoUrl(), req.branch(), req.actor());
    }

    /** 특정 프로그램 배포 — 작업 큐에 적재. */
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
        Program p = programService.requestRedeploy(id, who.name(), who.role() == Role.ADMIN,
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
