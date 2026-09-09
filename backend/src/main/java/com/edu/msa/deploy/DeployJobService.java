package com.edu.msa.deploy;

import com.edu.msa.common.DeployJobStatus;
import com.edu.msa.deploy.domain.DeployJob;
import com.edu.msa.deploy.dto.DeployDtos.DeployJobResponse;
import com.edu.msa.deploy.repository.DeployJobRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 배포 작업 큐: 적재(enqueue) / 선점(claim) / 완료·재시도(complete) / 방치 회수(requeueStale). */
@Service
public class DeployJobService {

    private final DeployJobRepository repo;
    private final Counter retries;

    /** 재시도 백오프 기본 간격(초) — base × 2^(attempts-1), 상한 max. */
    @Value("${edu.deploy.worker.retry-backoff-seconds:30}")
    private long retryBackoffSeconds;
    @Value("${edu.deploy.worker.retry-backoff-max-seconds:600}")
    private long retryBackoffMaxSeconds;
    /** RUNNING 인 채 이 시간(분) 넘게 갱신이 없으면 워커 사망으로 보고 회수한다(> Kaniko 최대 대기). */
    @Value("${edu.deploy.worker.stale-minutes:15}")
    private long staleMinutes;

    public DeployJobService(DeployJobRepository repo, MeterRegistry registry) {
        this.repo = repo;
        this.retries = Counter.builder("edu.deploy.jobs.retries")
                .description("배포 작업 재시도(재큐잉) 횟수")
                .register(registry);
    }

    @Transactional
    public DeployJobResponse enqueue(Long programId, String repoUrl, String branch, String actor) {
        return toResponse(repo.save(new DeployJob(programId, repoUrl, branch, actor)));
    }

    /** 대기 작업 하나를 선점해 RUNNING 으로 바꿔 반환한다(없으면 null). 행 잠금으로 중복 처리 방지. */
    @Transactional
    public DeployJob claimNext() {
        Long id = repo.claimNextId();
        if (id == null) return null;
        DeployJob j = repo.findById(id).orElse(null);
        if (j == null) return null;
        j.setStatus(DeployJobStatus.RUNNING);
        j.setAttempts(j.getAttempts() + 1);
        j.touch();
        return repo.save(j);
    }

    /**
     * 처리 결과 반영. 실패면 재시도 한도 내에서 지수 백오프와 함께 다시 QUEUED, 초과 시 FAILED.
     * (즉시 재큐잉하면 지속 실패 레포가 maxAttempts 를 순식간에 소진한다)
     */
    @Transactional
    public void complete(Long jobId, boolean success, Long deploymentId, String error) {
        DeployJob j = repo.findById(jobId).orElse(null);
        if (j == null) return;
        if (success) {
            j.setStatus(DeployJobStatus.DONE);
            j.setDeploymentId(deploymentId);
            j.setLastError(null);
            j.setNextAttemptAt(null);
        } else if (j.getAttempts() < j.getMaxAttempts()) {
            j.setStatus(DeployJobStatus.QUEUED);   // 재시도 — 백오프 후 claim 대상이 된다
            j.setLastError(error);
            long shift = Math.min(Math.max(j.getAttempts() - 1, 0), 6);
            long delay = Math.min(retryBackoffSeconds * (1L << shift), retryBackoffMaxSeconds);
            j.setNextAttemptAt(Instant.now().plusSeconds(delay));
            retries.increment();
        } else {
            j.setStatus(DeployJobStatus.FAILED);
            j.setLastError(error);
        }
        j.touch();
        repo.save(j);
    }

    /**
     * 워커 강제 종료로 방치된 RUNNING 작업 회수 — 유실 방지.
     * 임계는 최장 정상 처리 시간(Kaniko 대기 640s)보다 길게 잡는다.
     */
    @Transactional
    public int requeueStale() {
        Instant now = Instant.now();
        return repo.requeueStaleRunning(now.minus(Duration.ofMinutes(staleMinutes)), now);
    }

    @Transactional(readOnly = true)
    public List<DeployJobResponse> list() {
        return repo.findAllByOrderByIdDesc().stream().map(this::toResponse).toList();
    }

    private DeployJobResponse toResponse(DeployJob j) {
        return new DeployJobResponse(j.getId(), j.getProgramId(), j.getRepoUrl(), j.getBranch(),
                j.getStatus(), j.getAttempts(), j.getDeploymentId(), j.getLastError());
    }
}
