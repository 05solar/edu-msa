package com.edu.msa.notification.repository;

import com.edu.msa.notification.domain.Notification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByToUserOrderByIdDesc(String toUser);
    void deleteByProgramId(Long programId);

    /** 미읽음 개수 — 행을 가져오지 않고 DB COUNT 로 집계한다. */
    long countByToUserAndReadFalse(String toUser);

    /** 모두 읽음 — 행 로드·더티체킹 대신 벌크 UPDATE 한 방으로 처리한다. */
    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.read = true where n.toUser = :toUser and n.read = false")
    int markAllReadFor(@Param("toUser") String toUser);

    /**
     * 단일 읽음 — 소유자 조건을 UPDATE WHERE 에 포함해 조회·검사·수정 분리 없이
     * 원자적으로 처리한다(P1-1). 반환 0 = 미존재 또는 남의 알림(구분 불가가 의도).
     */
    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.read = true where n.id = :id and n.toUser = :toUser")
    int markReadOwned(@Param("id") Long id, @Param("toUser") String toUser);
}
