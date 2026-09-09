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
 * 배포 작업 큐 워커. 주기적으로 대기 작업을 선점해 실제 배포를 수행한다.
 * 다중 인스턴스에서 각자 폴링해도 DeployJobService.claimNext()의 행 잠금(FOR UPDATE SKIP LOCKED)
 * 덕분에 한 작업은 한 인스턴스만 처리한다.
 *
 * 운영에서는 API 파드(EDU_DEPLOY_WORKER_ENABLED=false)와 워커 Deployment(true)를 분리해
 * HTTP 트래픽과 큐 워크로드를 독립적으로 스케일한다(k8s/platform/backend-worker.yaml).
 *
 * 폴링당 1건이 아니라 큐가 빌 때까지 연속 처리(drain, 상한 max-per-tick)한다 —
 * 각 건은 여전히 claim→처리→complete 단위라 배치로 잡아 두는 일이 없다
 * (긴 배포 중 다른 건을 선점한 채 대기시키면 다른 워커가 처리하지 못한다).
 */
@Component
@ConditionalOnProperty(name = "edu.deploy.worker.enabled", havingValue = "true", matchIfMissing = true)
public class DeployWorker {

    private static final Logger log = LoggerFactory.getLogger(DeployWorker.class);

    private final DeployJobService jobs;
    private final DeploymentService deployments;

    /** 한 tick 에서 연속 처리할 최대 건수(과도한 독점 방지 안전 상한). */
    @org.springframework.beans.factory.annotation.Value("${edu.deploy.worker.max-per-tick:10}")
    private int maxPerTick;

    public DeployWorker(DeployJobService jobs, DeploymentService deployments) {
        this.jobs = jobs;
        this.deployments = deployments;
    }

    @Scheduled(fixedDelayString = "${edu.deploy.worker.poll-ms:3000}")
    public void tick() {
        // 워커 크래시로 방치된 RUNNING 회수(멱등) — 작업 유실 방지
        int requeued = jobs.requeueStale();
        if (requeued > 0) {
            log.warn("방치된 RUNNING 배포 작업 {}건을 QUEUED 로 회수했다", requeued);
        }
        for (int i = 0; i < maxPerTick; i++) {
            DeployJob job = jobs.claimNext();
            if (job == null) return;   // 큐 비면 다음 tick 까지 대기
            process(job);
        }
    }

    private void process(DeployJob job) {
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
