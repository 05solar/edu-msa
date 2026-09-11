package com.edu.msa.deploy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import org.springframework.data.domain.Persistable;

/**
 * slug 소유권 예약(P0-2). slug 가 PK 라서 동시 INSERT 경쟁에서 DB 가 단 한 배포만
 * 승자로 만든다 — 애플리케이션 exists 검사(TOCTOU)나 JVM 락에 의존하지 않으며,
 * backend/worker replica 가 몇 개든 동일하게 동작한다.
 *
 * Persistable 구현이 필수다: ID(slug)를 직접 할당하는 엔티티는 Spring Data 의
 * save() 가 merge(UPDATE)로 처리해 기존 소유자를 조용히 덮어쓴다 — isNew=true 로
 * 항상 persist(INSERT)를 강제해야 경쟁 패배가 제약 위반으로 드러난다.
 *
 * 수명: 최초 배포(검증 통과 직후)에 생성되고, 프로그램 삭제(removeFor) 때 반납된다.
 * program_id 가 null 인 행은 프로그램 없이 배포된(ad-hoc) slug 예약이다.
 */
@Entity
@Table(name = "slug_claims")
public class SlugClaim implements Persistable<String> {

    @Id
    @Column(length = 64)
    private String slug;

    private Long programId;

    private Instant createdAt = Instant.now();

    @Transient
    private boolean isNew = true;

    public SlugClaim() {}

    public SlugClaim(String slug, Long programId) {
        this.slug = slug;
        this.programId = programId;
    }

    @PostLoad
    @PostPersist
    void markNotNew() { this.isNew = false; }

    @Override
    public String getId() { return slug; }

    @Override
    public boolean isNew() { return isNew; }

    public String getSlug() { return slug; }
    public Long getProgramId() { return programId; }
    public Instant getCreatedAt() { return createdAt; }
}
