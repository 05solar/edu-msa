package com.edu.msa.notification;

import com.edu.msa.common.NotFoundException;
import com.edu.msa.common.NotiKind;
import com.edu.msa.common.Role;
import com.edu.msa.notification.domain.Notification;
import com.edu.msa.notification.dto.NotificationResponse;
import com.edu.msa.notification.repository.NotificationRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 서비스 — 수신 판정은 불변 UID(recipient_id = JWT uid) 또는 역할(recipient_role)로만
 * 한다(P1-5). to_user 는 표시 스냅샷일 뿐이며, 표시 이름이 같아도(동명이인) 알림이 섞이지
 * 않는다. recipient 가 모두 null 인 legacy 행은 안전하게 귀속할 수 없어 노출하지 않는다.
 */
@Service
public class NotificationService {

    private final NotificationRepository repo;

    public NotificationService(NotificationRepository repo) {
        this.repo = repo;
    }

    /** 특정 사용자(UID) 수신 알림. toUserDisplay 는 화면 표시용 스냅샷. */
    @Transactional
    public void push(Long recipientId, String toUserDisplay, NotiKind kind, String title, String sub, Long pid) {
        repo.save(Notification.toRecipient(recipientId, toUserDisplay, kind, title, sub, pid));
    }

    /** 역할 수신 공지 — 예: 프로그램 등록 요청 → ADMIN 전원(공유 수신함 의미: 한 명이 읽으면 읽음). */
    @Transactional
    public void pushToRole(Role role, String toUserDisplay, NotiKind kind, String title, String sub, Long pid) {
        repo.save(Notification.toRole(role, toUserDisplay, kind, title, sub, pid));
    }

    /**
     * 최신순 페이지 조회(P2-1) — 기본 20건·최대 100건(카탈로그 페이지네이션과 동일 관례).
     * 필터·정렬·LIMIT 은 전부 DB 가 수행한다.
     */
    @Transactional(readOnly = true)
    public com.edu.msa.common.PageResponse<NotificationResponse> listFor(Long uid, Role role, int page, int size) {
        var pageable = org.springframework.data.domain.PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return com.edu.msa.common.PageResponse.of(
                repo.findForRecipient(uid, role, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long uid, Role role) {
        return repo.countUnreadForRecipient(uid, role);
    }

    /**
     * 단일 읽음 — id 와 수신자(UID 또는 역할)가 함께 일치할 때만 변경한다(P1-1 유지·P1-5 UID 화).
     * 남의 알림 id 든 존재하지 않는 id 든 같은 404 라서 존재 여부가 노출되지 않는다.
     */
    @Transactional
    public void markRead(Long id, Long uid, Role role) {
        if (repo.markReadOwned(id, uid, role) == 0) {
            throw new NotFoundException("알림을 찾을 수 없습니다: " + id);
        }
    }

    /** 프로그램 삭제 시 해당 프로그램을 가리키는 알림을 함께 정리한다(클릭 시 404 방지). */
    @Transactional
    public void deleteForProgram(Long programId) {
        repo.deleteByProgramId(programId);
    }

    @Transactional
    public void markAllRead(Long uid, Role role) {
        repo.markAllReadFor(uid, role);
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getToUser(), n.getKind(), n.getTitle(), n.getSub(), n.isRead(), n.getProgramId());
    }
}
