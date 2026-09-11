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
        return repo.countByToUserAndReadFalse(name);
    }

    /**
     * 단일 읽음 처리 — id 와 소유자(toUser)가 함께 일치할 때만 변경한다(P1-1).
     * 남의 알림 id 든 존재하지 않는 id 든 같은 404 라서 존재 여부가 노출되지 않는다.
     */
    @Transactional
    public void markRead(Long id, String toUser) {
        if (repo.markReadOwned(id, toUser) == 0) {
            throw new NotFoundException("알림을 찾을 수 없습니다: " + id);
        }
    }

    /** 프로그램 삭제 시 해당 프로그램을 가리키는 알림을 함께 정리한다(클릭 시 404 방지). */
    @Transactional
    public void deleteForProgram(Long programId) {
        repo.deleteByProgramId(programId);
    }

    @Transactional
    public void markAllRead(String name) {
        repo.markAllReadFor(name);
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getToUser(), n.getKind(), n.getTitle(), n.getSub(), n.isRead(), n.getProgramId());
    }
}
