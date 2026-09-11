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

    /** active(중복 흡수 대상) 상태 — 이 상태의 작업이 있으면 같은 프로그램의 enqueue 는 멱등이다. */
    private static final List<DeployJobStatus> ACTIVE =
            List.of(DeployJobStatus.QUEUED, DeployJobStatus.RUNNING);

    private final Counter duplicateSuppressed;

    public DeployJobService(DeployJobRepository repo, MeterRegistry registry) {
        this.repo = repo;
        this.retries = Counter.builder("edu.deploy.jobs.retries")
                .description("배포 작업 재시도(재큐잉) 횟수")
                .register(registry);
        this.duplicateSuppressed = Counter.builder("edu.deploy.jobs.duplicate.suppressed")
                .description("중복 배포 요청 흡수(기존 active 작업 반환) 횟수")
                .register(registry);
    }

    /**
     * 배포 작업 적재 — 같은 프로그램의 active(QUEUED/RUNNING) 작업이 있으면 새로 만들지
     * 않고 그 작업을 반환한다(더블클릭·승인 중복·webhook replay·network retry 멱등 흡수).
     *
     * 아래 조회는 빠른 경로(UX)일 뿐이며, 동시 INSERT 경쟁의 최종 심판은 PostgreSQL 의
     * 부분 유니크 인덱스(uq_deploy_jobs_active_program, V3)다 — 경쟁에서 진 트랜잭션은
     * 제약 위반으로 롤백되고 GlobalExceptionHandler 가 409 로 변환한다.
     * JVM 락을 쓰지 않으므로 replica 몇 개에서든 동일하게 동작한다.
     */
    @Transactional
    public DeployJobResponse enqueue(Long programId, String repoUrl, String branch, String actor) {
        if (programId == null) {
            // [P2-3 불변식] 모든 배포는 프로그램에 소속된다 — slug 소유권(P0-2)·K8s
            // ownership/cleanup(P1-5)·중복 방지(partial unique)가 전부 프로그램 단위다.
            throw new IllegalArgumentException("프로그램 없는 배포는 지원하지 않습니다. 프로그램을 등록한 뒤 배포하세요.");
        }
        var existing = repo.findFirstByProgramIdAndStatusInOrderByIdDesc(programId, ACTIVE);
        if (existing.isPresent()) {
            duplicateSuppressed.increment();
            return toResponse(existing.get());
        }
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
     * 영구 오류(규격 검증 실패·slug 예약 충돌 등) — 재시도해도 해결되지 않으므로
     * attempts 잔여와 무관하게 즉시 FAILED terminal 로 종료한다(백오프 큐 재진입 금지).
     */
    @Transactional
    public void completeTerminal(Long jobId, String error) {
        DeployJob j = repo.findById(jobId).orElse(null);
        if (j == null) return;
        j.setStatus(DeployJobStatus.FAILED);
        j.setLastError(error);
        j.setNextAttemptAt(null);
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
