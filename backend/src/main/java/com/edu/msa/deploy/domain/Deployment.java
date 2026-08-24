package com.edu.msa.deploy.domain;

import com.edu.msa.common.DeploymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "deployments")
public class Deployment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long programId;
    private String slug;
    private String name;
    private String repoUrl;
    private String branch;
    private String imageTag;
    private String url;
    @Enumerated(EnumType.STRING)
    private DeploymentStatus status = DeploymentStatus.PENDING;
    @Column(columnDefinition = "text")
    private String logText;
    @Column(columnDefinition = "text")
    private String manifest;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public Deployment() {}

    public Deployment(Long programId, String repoUrl, String branch) {
        this.programId = programId;
        this.repoUrl = repoUrl;
        this.branch = branch;
    }

    public Long getId() { return id; }
    public Long getProgramId() { return programId; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRepoUrl() { return repoUrl; }
    public String getBranch() { return branch; }
    public String getImageTag() { return imageTag; }
    public void setImageTag(String imageTag) { this.imageTag = imageTag; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public DeploymentStatus getStatus() { return status; }
    public void setStatus(DeploymentStatus status) { this.status = status; this.updatedAt = Instant.now(); }
    public String getLogText() { return logText; }
    public void setLogText(String logText) { this.logText = logText; }
    public String getManifest() { return manifest; }
    public void setManifest(String manifest) { this.manifest = manifest; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
