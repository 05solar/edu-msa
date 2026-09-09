package com.edu.auth.session;

import com.edu.auth.session.repository.RefreshTokenRepository;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 만료된 refresh token 행 정리 스케줄러 — 로그인·회전마다 INSERT 되는
 * refresh_tokens 의 무한 증가를 막는다(정책·삭제 기준은 CleanupProperties 참조).
 *
 * 다중 replica 안전성: 삭제는 멱등이고 배치 단위 행 잠금이라, 여러 인스턴스가
 * 동시에 실행돼도 같은 행을 두 번 지우거나 충돌하지 않는다(중복 실행은 0건 삭제로 끝난다).
 * 배치는 각각 짧은 트랜잭션으로 커밋해 대량 백로그에서도 긴 잠금이 없다.
 */
@Component
@ConditionalOnProperty(name = "edu.auth.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class RefreshTokenCleaner {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleaner.class);

    private final RefreshTokenRepository refreshTokens;
    private final CleanupProperties props;
    private final TransactionTemplate tx;

    public RefreshTokenCleaner(RefreshTokenRepository refreshTokens, CleanupProperties props,
                               PlatformTransactionManager txManager) {
        this.refreshTokens = refreshTokens;
        this.props = props;
        this.tx = new TransactionTemplate(txManager);
    }

    @Scheduled(fixedDelayString = "${edu.auth.cleanup.interval-ms:3600000}",
            initialDelayString = "${edu.auth.cleanup.initial-delay-ms:60000}")
    public void tick() {
        try {
            int deleted = cleanOnce();
            if (deleted > 0) {
                log.info("refresh token 정리 완료 · {}건 삭제 (retention {}h)", deleted, props.getRetentionHours());
            }
        } catch (Exception e) {
            // 정리 실패가 스케줄러를 죽이면 안 된다 — 다음 주기에 재시도한다.
            log.error("refresh token 정리 실패: {}", e.getMessage(), e);
        }
    }

    /** 만료+보존기간이 지난 행을 배치 단위로 반복 삭제하고 총 삭제 건수를 반환한다. */
    public int cleanOnce() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusHours(props.getRetentionHours());
        int batch = Math.max(props.getBatchSize(), 1);
        int total = 0;
        while (true) {
            Integer deleted = tx.execute(s -> refreshTokens.deleteExpiredBatch(cutoff, batch));
            total += deleted == null ? 0 : deleted;
            if (deleted == null || deleted < batch) break;
        }
        return total;
    }
}
