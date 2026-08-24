package com.edu.msa.notification.domain;

import com.edu.msa.common.NotiKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "to_user")
    private String toUser;
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

    public Long getId() { return id; }
    public String getToUser() { return toUser; }
    public NotiKind getKind() { return kind; }
    public String getTitle() { return title; }
    public String getSub() { return sub; }
    public Long getProgramId() { return programId; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
}
