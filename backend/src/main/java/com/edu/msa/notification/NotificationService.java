package com.edu.msa.notification;

import com.edu.msa.common.NotFoundException;
import com.edu.msa.common.NotiKind;
import com.edu.msa.notification.domain.Notification;
import com.edu.msa.notification.dto.NotificationResponse;
import com.edu.msa.notification.repository.NotificationRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository repo;

    public NotificationService(NotificationRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void push(String to, NotiKind kind, String title, String sub, Long pid) {
        repo.save(new Notification(to, kind, title, sub, pid, false));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listFor(String name) {
        return repo.findByToUserOrderByIdDesc(name).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(String name) {
        return repo.findByToUserOrderByIdDesc(name).stream().filter(n -> !n.isRead()).count();
    }

    @Transactional
    public void markRead(Long id) {
        Notification n = repo.findById(id).orElseThrow(() -> new NotFoundException("알림을 찾을 수 없습니다: " + id));
        n.setRead(true);
    }

    /** 프로그램 삭제 시 해당 프로그램을 가리키는 알림을 함께 정리한다(클릭 시 404 방지). */
    @Transactional
    public void deleteForProgram(Long programId) {
        repo.deleteByProgramId(programId);
    }

    @Transactional
    public void markAllRead(String name) {
        repo.findByToUserOrderByIdDesc(name).forEach(n -> n.setRead(true));
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getToUser(), n.getKind(), n.getTitle(), n.getSub(), n.isRead(), n.getProgramId());
    }
}
