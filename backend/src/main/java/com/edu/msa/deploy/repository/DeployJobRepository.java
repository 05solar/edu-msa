package com.edu.msa.deploy.repository;

import com.edu.msa.deploy.domain.DeployJob;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeployJobRepository extends JpaRepository<DeployJob, Long> {

    /**
     * 다음 대기(QUEUED) 작업 하나를 원자적으로 선점한다.
     * FOR UPDATE SKIP LOCKED 로 다른 인스턴스가 잠근 행은 건너뛰어 중복 처리를 막는다. (PostgreSQL)
     * 재시도 백오프(next_attempt_at 미래)에 걸린 작업은 시각이 될 때까지 제외한다.
     */
    @Query(value = "SELECT id FROM deploy_jobs WHERE status = 'QUEUED' "
            + "AND (next_attempt_at IS NULL OR next_attempt_at <= now()) "
            + "ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    Long claimNextId();

    List<DeployJob> findAllByOrderByIdDesc();

    /** 같은 프로그램의 active(QUEUED/RUNNING) 작업 — 중복 enqueue 흡수(멱등)용. */
    java.util.Optional<DeployJob> findFirstByProgramIdAndStatusInOrderByIdDesc(
            Long programId, java.util.Collection<com.edu.msa.common.DeployJobStatus> statuses);

    void deleteByProgramId(Long programId);

    /** 상태별 건수 — 큐 메트릭(edu.deploy.jobs)용. */
    long countByStatus(com.edu.msa.common.DeployJobStatus status);

    /**
     * 워커 강제 종료로 RUNNING 인 채 방치된 작업 회수 — cutoff 이전에 갱신된 RUNNING 을
     * QUEUED 로 되돌려 다른 워커가 다시 집게 한다(작업 유실 방지). 멱등 UPDATE 라
     * 여러 워커가 동시에 실행해도 안전하다. attempts 는 재선점 시 다시 증가하므로
     * 반복 크래시는 maxAttempts 로 수렴한다.
     */
    @Modifying
    @Query("update DeployJob j set j.status = com.edu.msa.common.DeployJobStatus.QUEUED, "
            + "j.updatedAt = :now where j.status = com.edu.msa.common.DeployJobStatus.RUNNING "
            + "and j.updatedAt < :cutoff")
    int requeueStaleRunning(@Param("cutoff") java.time.Instant cutoff, @Param("now") java.time.Instant now);
}
