package com.edu.msa.deploy.repository;

import com.edu.msa.deploy.domain.SlugClaim;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlugClaimRepository extends JpaRepository<SlugClaim, String> {
    void deleteByProgramId(Long programId);
}
