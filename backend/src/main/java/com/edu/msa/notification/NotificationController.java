package com.edu.msa.notification;

import com.edu.msa.notification.dto.NotificationResponse;
import com.edu.msa.security.AuthPrincipal;

import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림 API — 대상 사용자는 항상 JWT principal 로 결정한다(P1-1 IDOR 수정).
 * 과거의 `?to=` 파라미터는 더 이상 읽지 않는다(전달돼도 무시 — 구버전 프론트 호환).
 * ADMIN 도 예외 없이 자기 알림만 본다(타인 알림 조회 기능 요구사항 없음).
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    /** 최신순 페이지 응답(P2-1) — 기본 20건, size 상한 100(카탈로그와 동일 관례). */
    @GetMapping
    public com.edu.msa.common.PageResponse<NotificationResponse> list(
            @AuthenticationPrincipal AuthPrincipal who,
            @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "20") int size) {
        return service.listFor(who.id(), who.role(), page, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AuthPrincipal who) {
        return Map.of("count", service.unreadCount(who.id(), who.role()));
    }

    @PostMapping("/{id}/read")
    public void read(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal who) {
        service.markRead(id, who.id(), who.role());
    }

    @PostMapping("/read-all")
    public void readAll(@AuthenticationPrincipal AuthPrincipal who) {
        service.markAllRead(who.id(), who.role());
    }
}
