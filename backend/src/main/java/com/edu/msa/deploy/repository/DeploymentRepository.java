package com.edu.msa.deploy.repository;

import com.edu.msa.deploy.domain.Deployment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentRepository extends JpaRepository<Deployment, Long> {
    List<Deployment> findByProgramIdOrderByIdDesc(Long programId);
    Optional<Deployment> findTopByProgramIdOrderByIdDesc(Long programId);
    boolean existsBySlug(String slug);
}
