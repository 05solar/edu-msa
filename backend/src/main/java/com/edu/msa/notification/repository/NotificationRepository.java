package com.edu.msa.notification.repository;

import com.edu.msa.common.Role;
import com.edu.msa.notification.domain.Notification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 수신 판정은 recipient_id(불변 UID) 또는 recipient_role 로만 한다(P1-5).
 * to_user(표시 이름)는 어떤 쿼리의 조건에도 쓰지 않는다 — 동명이인 격리.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * 최신순 페이지 조회(P2-1) — 필터·정렬·LIMIT 전부 DB 에서 수행한다(전건 로드 금지).
     * 정렬은 created_at DESC + id DESC(동일 시각 tie-breaker — 페이지 사이 중복/누락 방지).
     */
    @Query(value = "select n from Notification n where n.recipientId = :uid "
            + "or (n.recipientRole is not null and n.recipientRole = :role) "
            + "order by n.createdAt desc, n.id desc",
            countQuery = "select count(n) from Notification n where n.recipientId = :uid "
                    + "or (n.recipientRole is not null and n.recipientRole = :role)")
    org.springframework.data.domain.Page<Notification> findForRecipient(
            @Param("uid") Long uid, @Param("role") Role role,
            org.springframework.data.domain.Pageable pageable);

    /**
     * 보존 정책(P2-1) — "읽은" 알림만 retention 경과 후 배치 삭제한다(미읽음은 절대 자동
     * 삭제하지 않는다). LIMIT 서브쿼리로 한 번에 지우는 양을 제한해 긴 잠금이 없고,
     * 삭제는 멱등이라 여러 replica 가 동시에 돌아도 충돌하지 않는다(auth cleaner 패턴).
     * 파생 테이블(x)로 한 번 감싼 이유: MariaDB 는 삭제 대상 테이블을 서브쿼리에서
     * 직접 참조하지 못한다(Error 1093) — 이 형태는 H2 에서도 동일하게 동작한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM notifications WHERE id IN (SELECT id FROM "
            + "(SELECT id FROM notifications WHERE is_read = true AND created_at < :cutoff LIMIT :batch) x)",
            nativeQuery = true)
    int deleteOldReadBatch(@Param("cutoff") java.time.Instant cutoff, @Param("batch") int batch);

    void deleteByProgramId(Long programId);

    /** 미읽음 개수 — 행을 가져오지 않고 DB COUNT 로 집계한다. */
    @Query("select count(n) from Notification n where (n.recipientId = :uid "
            + "or (n.recipientRole is not null and n.recipientRole = :role)) and n.read = false")
    long countUnreadForRecipient(@Param("uid") Long uid, @Param("role") Role role);

    /** 모두 읽음 — 행 로드·더티체킹 대신 벌크 UPDATE 한 방으로 처리한다. */
    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.read = true where (n.recipientId = :uid "
            + "or (n.recipientRole is not null and n.recipientRole = :role)) and n.read = false")
    int markAllReadFor(@Param("uid") Long uid, @Param("role") Role role);

    /**
     * 단일 읽음 — 수신자 조건을 UPDATE WHERE 에 포함해 조회·검사·수정 분리 없이
     * 원자적으로 처리한다. 반환 0 = 미존재 또는 남의 알림(구분 불가가 의도).
     */
    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.read = true where n.id = :id and (n.recipientId = :uid "
            + "or (n.recipientRole is not null and n.recipientRole = :role))")
    int markReadOwned(@Param("id") Long id, @Param("uid") Long uid, @Param("role") Role role);

    /** 테스트/정리용 — 표시 이름 조회는 판정에 쓰지 않는다. */
    List<Notification> findByToUserOrderByIdDesc(String toUser);
}
