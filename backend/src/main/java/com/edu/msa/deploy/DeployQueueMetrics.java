package com.edu.msa.deploy;

import com.edu.msa.common.DeployJobStatus;
import com.edu.msa.deploy.repository.DeployJobRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 배포 큐 메트릭 — Prometheus 스크레이프마다 상태별 건수를 집계한다(status 인덱스 COUNT).
 *
 *   edu_deploy_queue_depth              대기(QUEUED) 건수 — 워커 HPA(KEDA) 기준 지표
 *   edu_deploy_jobs{status=...}         queued/running/done/failed 건수
 *   edu_deploy_jobs_retries_total       재시도 횟수(DeployJobService 카운터)
 */
@Component
public class DeployQueueMetrics {

    public DeployQueueMetrics(DeployJobRepository repo, MeterRegistry registry) {
        Gauge.builder("edu.deploy.queue.depth", () -> repo.countByStatus(DeployJobStatus.QUEUED))
                .description("대기 중(QUEUED) 배포 작업 수 — 워커 스케일아웃 기준")
                .register(registry);
        for (DeployJobStatus status : DeployJobStatus.values()) {
            Gauge.builder("edu.deploy.jobs", () -> repo.countByStatus(status))
                    .tag("status", status.name().toLowerCase())
                    .description("상태별 배포 작업 수")
                    .register(registry);
        }
    }
}
