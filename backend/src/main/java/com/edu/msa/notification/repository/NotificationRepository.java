package com.edu.msa.notification.repository;

import com.edu.msa.notification.domain.Notification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByToUserOrderByIdDesc(String toUser);
    void deleteByProgramId(Long programId);
}
