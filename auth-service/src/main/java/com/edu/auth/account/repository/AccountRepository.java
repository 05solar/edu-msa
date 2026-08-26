package com.edu.auth.account.repository;

import com.edu.auth.account.domain.Account;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByUsername(String username);
    Optional<Account> findByEmailIgnoreCase(String email);
    boolean existsByUsername(String username);
    boolean existsByEmailIgnoreCase(String email);

    /** 상향 권한 승인 대기 목록(신청 순). */
    List<Account> findByRequestedRoleIsNotNullOrderByCreatedAtAsc();
}
