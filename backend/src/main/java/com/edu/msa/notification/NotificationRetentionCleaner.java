package com.edu.msa.notification;

import com.edu.msa.notification.repository.NotificationRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 알림 보존 정책(P2-1) — notifications 의 무한 증가를 막는다.
 *
 * 삭제 대상은 "읽음 처리된 지 오래된 행"뿐이다:
 *   is_read = true AND created_at < now() - retention(기본 90일)
 * 미읽음 알림은 절대 자동 삭제하지 않는다(사용자가 아직 못 본 정보).
 * 별도의 법적/감사 보존 요구는 코드·문서에 없다 — 감사 기록은 review_logs 가 담당한다.
 *
 * 다중 replica 안전성: 삭제는 멱등이고 배치 단위(짧은 트랜잭션 반복)라 여러 인스턴스가
 * 동시에 실행돼도 충돌하지 않는다(auth RefreshTokenCleaner 와 동일 패턴).
 * 실패는 WARN 후 다음 주기에 재시도한다(주요 기능 무영향, 즉시 재시도 없음).
 */
@Component
@ConditionalOnProperty(name = "edu.notification.cleanup-enabled", havingValue = "true", matchIfMissing = true)
public class NotificationRetentionCleaner {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetentionCleaner.class);

    private final NotificationRepository repo;
    private final TransactionTemplate tx;

    @Value("${edu.notification.retention-days:90}")
    private long retentionDays;
    @Value("${edu.notification.cleanup-batch-size:500}")
    private int batchSize;

    public NotificationRetentionCleaner(NotificationRepository repo, PlatformTransactionManager txManager) {
        this.repo = repo;
        this.tx = new TransactionTemplate(txManager);
    }

    @Scheduled(fixedDelayString = "${edu.notification.cleanup-interval-ms:3600000}",
            initialDelayString = "${edu.notification.cleanup-initial-delay-ms:120000}")
    public void tick() {
        try {
            int deleted = cleanOnce();
            if (deleted > 0) {
                log.info("읽은 알림 정리 완료 · {}건 삭제 (retention {}일)", deleted, retentionDays);
            }
        } catch (Exception e) {
            // 정리 실패가 스케줄러·주요 기능을 죽이면 안 된다 — 다음 주기에 재시도한다.
            log.warn("알림 정리 실패(다음 주기 재시도): {}", e.getMessage());
        }
    }

    /** 읽은 지 오래된 알림을 배치 단위로 반복 삭제하고 총 삭제 건수를 반환한다. */
    public int cleanOnce() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        int batch = Math.max(batchSize, 1);
        int total = 0;
        while (true) {
            Integer deleted = tx.execute(s -> repo.deleteOldReadBatch(cutoff, batch));
            total += deleted == null ? 0 : deleted;
            if (deleted == null || deleted < batch) break;
        }
        return total;
    }
}
