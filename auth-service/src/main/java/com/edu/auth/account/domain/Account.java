package com.edu.auth.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 계정 정보의 단일 소스. 플랫폼 backend 의 app_users 와 달리 자격 증명까지 보관한다.
 * 비밀번호는 BCrypt 해시만 저장하며 평문은 어떤 경로로도 남기지 않는다.
 */
@Entity
@Table(
        name = "accounts",
        indexes = {
                @Index(name = "ux_accounts_username", columnList = "username", unique = true),
                @Index(name = "ux_accounts_email", columnList = "email", unique = true)
        }
)
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 190)
    private String email;

    @Column(nullable = false, length = 50)
    private String dept;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AccountRole role;

    /**
     * 최초 로그인 시 비밀번호 변경 강제를 위한 플래그.
     * 시드 계정처럼 공통 임시 비밀번호로 발급된 계정은 true 로 저장한다.
     * (강제 변경 화면 자체는 추후 작업 범위)
     */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected Account() {}

    public Account(String username, String passwordHash, String name, String email,
                   String dept, AccountRole role, boolean mustChangePassword) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.name = name;
        this.email = email;
        this.dept = dept;
        this.role = role;
        this.mustChangePassword = mustChangePassword;
    }

    @PreUpdate
    void touch() { this.updatedAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getDept() { return dept; }
    public AccountRole getRole() { return role; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    /** 권한 부여는 운영 관리자만 수행한다(회원가입 기본값은 USER). */
    public void changeRole(AccountRole role) { this.role = role; }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.mustChangePassword = false;
    }
}
