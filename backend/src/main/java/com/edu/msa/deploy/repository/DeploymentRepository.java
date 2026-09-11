package com.edu.msa.deploy.repository;

import com.edu.msa.deploy.domain.Deployment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentRepository extends JpaRepository<Deployment, Long> {
    List<Deployment> findByProgramIdOrderByIdDesc(Long programId);
    Optional<Deployment> findTopByProgramIdOrderByIdDesc(Long programId);
    // slug 중복 검사는 deployments 이력이 아니라 slug_claims(소유권 예약)가 담당한다(P0-2).
    void deleteByProgramId(Long programId);
}
