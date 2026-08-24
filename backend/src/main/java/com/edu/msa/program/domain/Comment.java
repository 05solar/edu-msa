package com.edu.msa.program.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "comments")
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long programId;
    @Column(name = "comment_user")
    private String user;
    private String dept;
    @Column(name = "comment_time")
    private String time;
    @Column(columnDefinition = "text")
    private String body;

    private String replyUser;
    private String replyDept;
    private String replyTime;
    @Column(columnDefinition = "text")
    private String replyBody;

    protected Comment() {}

    public Comment(Long programId, String user, String dept, String time, String body) {
        this.programId = programId;
        this.user = user;
        this.dept = dept;
        this.time = time;
        this.body = body;
    }

    public void setReply(String user, String dept, String time, String body) {
        this.replyUser = user;
        this.replyDept = dept;
        this.replyTime = time;
        this.replyBody = body;
    }

    public boolean hasReply() { return replyBody != null; }

    public Long getId() { return id; }
    public Long getProgramId() { return programId; }
    public String getUser() { return user; }
    public String getDept() { return dept; }
    public String getTime() { return time; }
    public String getBody() { return body; }
    public String getReplyUser() { return replyUser; }
    public String getReplyDept() { return replyDept; }
    public String getReplyTime() { return replyTime; }
    public String getReplyBody() { return replyBody; }
}
