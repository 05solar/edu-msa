package com.edu.msa.deploy;

import com.edu.msa.common.DeploymentStatus;
import com.edu.msa.deploy.domain.DeployJob;
import com.edu.msa.deploy.dto.DeployDtos.DeployRequest;
import com.edu.msa.deploy.dto.DeployDtos.DeploymentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 배포 작업 큐 워커. 주기적으로 대기 작업을 하나씩 선점해 실제 배포를 수행한다.
 * 다중 인스턴스에서 각자 폴링해도 DeployJobService.claimNext()의 행 잠금(FOR UPDATE SKIP LOCKED)
 * 덕분에 한 작업은 한 인스턴스만 처리한다.
 */
@Component
@ConditionalOnProperty(name = "edu.deploy.worker.enabled", havingValue = "true", matchIfMissing = true)
public class DeployWorker {

    private static final Logger log = LoggerFactory.getLogger(DeployWorker.class);

    private final DeployJobService jobs;
    private final DeploymentService deployments;

    public DeployWorker(DeployJobService jobs, DeploymentService deployments) {
        this.jobs = jobs;
        this.deployments = deployments;
    }

    @Scheduled(fixedDelayString = "${edu.deploy.worker.poll-ms:3000}")
    public void tick() {
        DeployJob job = jobs.claimNext();
        if (job == null) return;
        log.info("배포 작업 처리 #{} (시도 {}/{}) repo={}", job.getId(), job.getAttempts(), job.getMaxAttempts(), job.getRepoUrl());
        try {
            DeploymentResponse dep = deployments.deploy(
                    new DeployRequest(job.getProgramId(), job.getRepoUrl(), job.getBranch(), job.getActor()));
            boolean ok = dep.status() == DeploymentStatus.RUNNING;
            jobs.complete(job.getId(), ok, dep.id(), ok ? null : ("배포 상태=" + dep.status()));
        } catch (Exception e) {
            jobs.complete(job.getId(), false, null, e.getMessage());
        }
    }
}
