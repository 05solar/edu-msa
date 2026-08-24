package com.edu.msa.deploy;

import com.edu.msa.deploy.dto.DeployDtos.DeployRequest;
import com.edu.msa.deploy.dto.DeployDtos.DeploymentResponse;
import com.edu.msa.deploy.dto.DeployDtos.ValidateRequest;
import com.edu.msa.deploy.dto.DeployDtos.ValidationResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DeploymentController {

    private final DeploymentService service;

    public DeploymentController(DeploymentService service) {
        this.service = service;
    }

    /** 등록 화면에서 레포 규격을 사전 검증한다. */
    @PostMapping("/deploy/validate")
    public ValidationResult validate(@Valid @RequestBody ValidateRequest req) {
        return service.validate(req);
    }

    /** 임의 레포 배포(programId 미연결). */
    @PostMapping("/deploy")
    public DeploymentResponse deploy(@Valid @RequestBody DeployRequest req) {
        return service.deploy(req);
    }

    /** 특정 프로그램을 배포한다(승인 대신 실제 배포로 공개 전환). */
    @PostMapping("/programs/{id}/deploy")
    public DeploymentResponse deployProgram(@PathVariable Long id, @Valid @RequestBody DeployRequest req) {
        return service.deploy(new DeployRequest(id, req.repoUrl(), req.branch(), req.actor()));
    }

    @GetMapping("/programs/{id}/deployment")
    public DeploymentResponse latest(@PathVariable Long id) {
        return service.latest(id);
    }
}
