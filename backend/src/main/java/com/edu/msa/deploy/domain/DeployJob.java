package com.edu.msa.deploy.domain;

import com.edu.msa.common.DeployJobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 배포 작업 큐 항목 — 인메모리 스레드풀 대신 DB에 적재해 다중 인스턴스에서 안전하게 처리한다. */
@Entity
@Table(name = "deploy_jobs", indexes = {
        // 큐 폴링(claim)·상태별 카운트 메트릭용
        @jakarta.persistence.Index(name = "idx_deploy_jobs_status", columnList = "status"),
})
public class DeployJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long programId;
    @Column(columnDefinition = "text")
    private String repoUrl;
    private String branch;
    private String actor;

    @Enumerated(EnumType.STRING)
    private DeployJobStatus status = DeployJobStatus.QUEUED;
    private int attempts = 0;
    private int maxAttempts = 2;
    private Long deploymentId;
    @Column(columnDefinition = "text")
    private String lastError;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    /** 재시도 백오프 — 이 시각 전에는 claim 대상에서 제외된다(null = 즉시 가능). */
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    public DeployJob() {}

    public DeployJob(Long programId, String repoUrl, String branch, String actor) {
        this.programId = programId;
        this.repoUrl = repoUrl;
        this.branch = branch;
        this.actor = actor;
    }

    public void touch() { this.updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getProgramId() { return programId; }
    public String getRepoUrl() { return repoUrl; }
    public String getBranch() { return branch; }
    public String getActor() { return actor; }
    public DeployJobStatus getStatus() { return status; }
    public void setStatus(DeployJobStatus status) { this.status = status; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public int getMaxAttempts() { return maxAttempts; }
    public Long getDeploymentId() { return deploymentId; }
    public void setDeploymentId(Long deploymentId) { this.deploymentId = deploymentId; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
}
