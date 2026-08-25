package com.edu.auth.session.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 발급된 Refresh Token 의 서버 측 기록.
 * 원문은 저장하지 않고 SHA-256 해시만 보관하며, 갱신 시 회전(rotate)시켜 재사용을 차단한다.
 */
@Entity
@Table(
        name = "refresh_tokens",
        indexes = {
                @Index(name = "ux_refresh_token_hash", columnList = "token_hash", unique = true),
                @Index(name = "ix_refresh_account", columnList = "account_id")
        }
)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected RefreshToken() {}

    public RefreshToken(Long accountId, String tokenHash, OffsetDateTime expiresAt) {
        this.accountId = accountId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
    public String getTokenHash() { return tokenHash; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public boolean isRevoked() { return revoked; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void revoke() { this.revoked = true; }

    public boolean isUsable() {
        return !revoked && expiresAt.isAfter(OffsetDateTime.now());
    }
}
