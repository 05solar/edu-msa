package com.edu.msa.deploy;

import com.edu.msa.deploy.domain.SlugClaim;
import com.edu.msa.deploy.repository.SlugClaimRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * slug 소유권 예약(P0-2). 동시성 원자성은 slug_claims 의 PK(INSERT 경쟁)가 담당한다.
 *
 * 예약 INSERT 는 독립 트랜잭션(REQUIRES_NEW)에서 시도한다 — PostgreSQL 은 제약 위반
 * 시 트랜잭션 전체를 abort 하므로, 바깥(배포 파이프라인의 짧은 상태 트랜잭션들)을
 * 오염시키지 않으려면 위반 가능성이 있는 INSERT 를 격리해야 한다.
 */
@Component
public class SlugClaims {

    private static final Logger log = LoggerFactory.getLogger(SlugClaims.class);

    private final SlugClaimRepository repo;
    private final TransactionTemplate txNew;
    private final Counter conflicts;

    public SlugClaims(SlugClaimRepository repo, PlatformTransactionManager txManager,
                      MeterRegistry registry) {
        this.repo = repo;
        this.txNew = new TransactionTemplate(txManager);
        this.txNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.conflicts = Counter.builder("edu.deploy.slug.conflicts")
                .description("slug 예약 충돌(다른 프로그램이 선점) 횟수")
                .register(registry);
    }

    /**
     * slug 소유권을 예약한다. true = 이 프로그램이 소유(신규 획득 또는 기존 소유 재확인).
     * false = 다른 프로그램(또는 소유 불명 예약)이 선점 — 영구 오류로 처리해야 한다.
     */
    public boolean claim(String slug, Long programId) {
        try {
            txNew.executeWithoutResult(s -> {
                repo.saveAndFlush(new SlugClaim(slug, programId));
            });
            return true;
        } catch (DataIntegrityViolationException raced) {
            // 이미 예약된 slug — 소유자가 같은 프로그램이면 재배포로 허용한다.
            Long owner = txNew.execute(s ->
                    repo.findById(slug).map(SlugClaim::getProgramId).orElse(null));
            boolean owned = programId != null && Objects.equals(owner, programId);
            if (!owned) {
                conflicts.increment();
                log.warn("slug 예약 충돌 — 요청 프로그램과 기존 소유가 다르다 (slug 는 로그에만 남긴다): {}", slug);
            }
            return owned;
        }
    }
}
