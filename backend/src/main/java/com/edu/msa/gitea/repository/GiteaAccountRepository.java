package com.edu.msa.gitea.repository;

import com.edu.msa.gitea.domain.GiteaAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GiteaAccountRepository extends JpaRepository<GiteaAccount, Long> {
    Optional<GiteaAccount> findByAccountId(Long accountId);
}
