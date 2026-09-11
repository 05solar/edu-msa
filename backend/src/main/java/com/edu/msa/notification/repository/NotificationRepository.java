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

    @Query("select n from Notification n where n.recipientId = :uid "
            + "or (n.recipientRole is not null and n.recipientRole = :role) order by n.id desc")
    List<Notification> findForRecipient(@Param("uid") Long uid, @Param("role") Role role);

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
