package com.edu.msa.deploy.repository;

import com.edu.msa.deploy.domain.DeployJob;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DeployJobRepository extends JpaRepository<DeployJob, Long> {

    /**
     * 다음 대기(QUEUED) 작업 하나를 원자적으로 선점한다.
     * FOR UPDATE SKIP LOCKED 로 다른 인스턴스가 잠근 행은 건너뛰어 중복 처리를 막는다. (PostgreSQL)
     */
    @Query(value = "SELECT id FROM deploy_jobs WHERE status = 'QUEUED' ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    Long claimNextId();

    List<DeployJob> findAllByOrderByIdDesc();
}
