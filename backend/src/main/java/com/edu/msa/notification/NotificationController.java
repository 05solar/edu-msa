package com.edu.msa.notification;

import com.edu.msa.notification.dto.NotificationResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public List<NotificationResponse> list(@RequestParam String to) {
        return service.listFor(to);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@RequestParam String to) {
        return Map.of("count", service.unreadCount(to));
    }

    @PostMapping("/{id}/read")
    public void read(@PathVariable Long id) {
        service.markRead(id);
    }

    @PostMapping("/read-all")
    public void readAll(@RequestParam String to) {
        service.markAllRead(to);
    }
}
