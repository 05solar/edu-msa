package com.edu.msa.notification.domain;

import com.edu.msa.common.NotiKind;
import com.edu.msa.common.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "notifications", indexes = {
        // 사용자별 목록 조회·미읽음 카운트가 인덱스를 타게 한다.
        @Index(name = "idx_notifications_to_read", columnList = "to_user, is_read"),
        @Index(name = "idx_notifications_recipient_read", columnList = "recipient_id, is_read"),
})
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 표시용 스냅샷 — 수신 판정에 사용 금지(P1-5). 판정은 recipientId/recipientRole 로만 한다. */
    @Column(name = "to_user")
    private String toUser;
    /** 수신자 불변 UID(JWT uid). null 이면 recipientRole 기반 공지이거나 legacy(비노출). */
    @Column(name = "recipient_id")
    private Long recipientId;
    /** 역할 수신 공지(예: 등록 요청 → 관리자 전원). recipientId 와 배타적으로 사용. */
    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_role")
    private Role recipientRole;
    @Enumerated(EnumType.STRING)
    private NotiKind kind;
    @Column(columnDefinition = "text")
    private String title;
    @Column(columnDefinition = "text")
    private String sub;
    private Long programId;
    @Column(name = "is_read")
    private boolean read;

    protected Notification() {}

    public Notification(String toUser, NotiKind kind, String title, String sub, Long programId, boolean read) {
        this.toUser = toUser;
        this.kind = kind;
        this.title = title;
        this.sub = sub;
        this.programId = programId;
        this.read = read;
    }

    /** UID 수신자 알림(P1-5) — toUser 는 표시 스냅샷일 뿐 판정에 쓰지 않는다. */
    public static Notification toRecipient(Long recipientId, String toUserDisplay, NotiKind kind,
                                           String title, String sub, Long programId) {
        Notification n = new Notification(toUserDisplay, kind, title, sub, programId, false);
        n.recipientId = recipientId;
        return n;
    }

    /** 역할 수신 공지(P1-5) — 예: 프로그램 등록 요청 → ADMIN 전원. */
    public static Notification toRole(Role role, String toUserDisplay, NotiKind kind,
                                      String title, String sub, Long programId) {
        Notification n = new Notification(toUserDisplay, kind, title, sub, programId, false);
        n.recipientRole = role;
        return n;
    }

    public Long getId() { return id; }
    public String getToUser() { return toUser; }
    public Long getRecipientId() { return recipientId; }
    public Role getRecipientRole() { return recipientRole; }
    public NotiKind getKind() { return kind; }
    public String getTitle() { return title; }
    public String getSub() { return sub; }
    public Long getProgramId() { return programId; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
}
