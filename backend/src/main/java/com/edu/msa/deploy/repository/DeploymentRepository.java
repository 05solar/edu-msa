package com.edu.msa.deploy.repository;

import com.edu.msa.deploy.domain.Deployment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentRepository extends JpaRepository<Deployment, Long> {
    List<Deployment> findByProgramIdOrderByIdDesc(Long programId);
    Optional<Deployment> findTopByProgramIdOrderByIdDesc(Long programId);
    boolean existsBySlug(String slug);
    // 같은 프로그램의 재배포는 허용하고, 다른 프로그램이 같은 slug를 쓰는 경우만 중복으로 본다.
    boolean existsBySlugAndProgramIdNot(String slug, Long programId);
    void deleteByProgramId(Long programId);
}
