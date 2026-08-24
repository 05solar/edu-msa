package com.edu.msa.program.domain;

import com.edu.msa.common.ProgramStatus;
import com.edu.msa.common.Scope;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "programs")
public class Program {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    @Column(unique = true)
    private String slug;
    private String cat;
    private String owner;
    private String dept;
    @Column(name = "app_version")
    private String version;

    @Column(columnDefinition = "text")
    private String summary;
    @Column(columnDefinition = "text")
    private String description;

    private String repoUrl;
    private String branch;

    @Enumerated(EnumType.STRING)
    private ProgramStatus status = ProgramStatus.PENDING;
    @Enumerated(EnumType.STRING)
    private Scope scope = Scope.ALL;

    private int views;
    private int likes;
    private int downloads;

    private LocalDate createdAt;
    private LocalDate updatedAt;

    @Column(columnDefinition = "text")
    private String rejectReason;
    @Column(columnDefinition = "text")
    private String stopReason;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "program_tags", joinColumns = @JoinColumn(name = "program_id"))
    @Column(name = "tag")
    private List<String> tags = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "program_tech", joinColumns = @JoinColumn(name = "program_id"))
    @Column(name = "tech")
    private List<String> tech = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "program_purposes", joinColumns = @JoinColumn(name = "program_id"))
    @Column(name = "purpose")
    private List<String> purposes = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "program_run", joinColumns = @JoinColumn(name = "program_id"))
    @Column(name = "run_type")
    private List<String> run = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "program_features", joinColumns = @JoinColumn(name = "program_id"))
    @OrderColumn(name = "idx")
    @Column(name = "feature", columnDefinition = "text")
    private List<String> features = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "program_readme", joinColumns = @JoinColumn(name = "program_id"))
    @OrderColumn(name = "idx")
    @Column(name = "line", columnDefinition = "text")
    private List<String> readme = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "program_history", joinColumns = @JoinColumn(name = "program_id"))
    @OrderColumn(name = "idx")
    private List<HistoryEntry> history = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "program_files", joinColumns = @JoinColumn(name = "program_id"))
    @OrderColumn(name = "idx")
    private List<ProgramFile> files = new ArrayList<>();

    public Program() {}

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getCat() { return cat; }
    public void setCat(String cat) { this.cat = cat; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public String getDept() { return dept; }
    public void setDept(String dept) { this.dept = dept; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getRepoUrl() { return repoUrl; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public ProgramStatus getStatus() { return status; }
    public void setStatus(ProgramStatus status) { this.status = status; }
    public Scope getScope() { return scope; }
    public void setScope(Scope scope) { this.scope = scope; }
    public int getViews() { return views; }
    public void setViews(int views) { this.views = views; }
    public int getLikes() { return likes; }
    public void setLikes(int likes) { this.likes = likes; }
    public int getDownloads() { return downloads; }
    public void setDownloads(int downloads) { this.downloads = downloads; }
    public LocalDate getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDate createdAt) { this.createdAt = createdAt; }
    public LocalDate getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDate updatedAt) { this.updatedAt = updatedAt; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public String getStopReason() { return stopReason; }
    public void setStopReason(String stopReason) { this.stopReason = stopReason; }
    public List<String> getTags() { return tags; }
    public List<String> getTech() { return tech; }
    public List<String> getPurposes() { return purposes; }
    public List<String> getRun() { return run; }
    public List<String> getFeatures() { return features; }
    public List<String> getReadme() { return readme; }
    public List<HistoryEntry> getHistory() { return history; }
    public List<ProgramFile> getFiles() { return files; }
}
