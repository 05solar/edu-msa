package com.edu.msa.deploy.dto;

import com.edu.msa.common.DeploymentStatus;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

public final class DeployDtos {
    private DeployDtos() {}

    public record ValidateRequest(
            @NotBlank String repoUrl,
            String branch,
            String serviceYaml   // 선택: 인라인 규격(레포 접근 없이 검증)
    ) {}

    public record SpecView(
            String name, String slug, String category,
            List<String> purposes, List<String> tech, String summary,
            int port, String health
    ) {}

    public record ValidationResult(
            boolean valid, List<String> errors, SpecView spec, String resolvedFrom
    ) {}

    public record DeployRequest(
            Long programId,
            @NotBlank String repoUrl,
            String branch,
            String actor
    ) {}

    /** 등록자 본인 재배포 요청 — 레포 주소는 서버에 저장된 값을 쓰므로 받지 않는다. */
    public record RedeployRequest(
            String version,
            String note
    ) {}

    public record DeploymentResponse(
            Long id, Long programId, String slug, String name,
            DeploymentStatus status, String url, String imageTag, String mode,
            String manifest, String log, Instant createdAt
    ) {}

    public record DeployJobResponse(
            Long id, Long programId, String repoUrl, String branch,
            com.edu.msa.common.DeployJobStatus status, int attempts,
            Long deploymentId, String lastError
    ) {}
}
