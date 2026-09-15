package com.edu.msa.gitea.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 포털 사용자(auth 계정 uid)가 셀프 발급한 Gitea 계정 매핑.
 * 비밀번호 등 계정 실체는 Gitea 가 소유한다 — 여기는 발급 사실과 아이디만 기록.
 */
@Entity
@Table(name = "gitea_accounts")
public class GiteaAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** auth 계정의 불변 UID (JWT uid) — 사용자당 1계정. */
    @Column(name = "account_id", nullable = false, unique = true)
    private Long accountId;

    @Column(name = "gitea_username", nullable = false, unique = true, length = 40)
    private String giteaUsername;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected GiteaAccount() {}

    public GiteaAccount(Long accountId, String giteaUsername) {
        this.accountId = accountId;
        this.giteaUsername = giteaUsername;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
    public String getGiteaUsername() { return giteaUsername; }
    public Instant getCreatedAt() { return createdAt; }
}
