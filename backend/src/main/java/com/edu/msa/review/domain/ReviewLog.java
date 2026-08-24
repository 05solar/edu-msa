package com.edu.msa.review.domain;

import com.edu.msa.common.ReviewAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "review_logs")
public class ReviewLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "logged_at")
    private String at;
    private Long programId;
    private String title;
    @Column(name = "reviewer")
    private String by;
    @Enumerated(EnumType.STRING)
    private ReviewAction action;
    @Column(columnDefinition = "text")
    private String memo;

    protected ReviewLog() {}

    public ReviewLog(String at, Long programId, String title, String by, ReviewAction action, String memo) {
        this.at = at;
        this.programId = programId;
        this.title = title;
        this.by = by;
        this.action = action;
        this.memo = memo;
    }

    public Long getId() { return id; }
    public String getAt() { return at; }
    public Long getProgramId() { return programId; }
    public String getTitle() { return title; }
    public String getBy() { return by; }
    public ReviewAction getAction() { return action; }
    public String getMemo() { return memo; }
}
