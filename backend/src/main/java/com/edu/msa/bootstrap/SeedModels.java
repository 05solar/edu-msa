package com.edu.msa.bootstrap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** resources/seed/*.json 매핑용 DTO (프론트엔드 목업 데이터 키와 동일). */
public final class SeedModels {
    private SeedModels() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HistorySeed(String ver, String date, String log) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FileSeed(String name, String size, String type) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReplySeed(String user, String dept, String time, String body) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommentSeed(String user, String dept, String time, String body, ReplySeed reply) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProgramSeed(
            Long id, String name, String cat, String owner, String dept, String ver,
            String updated, String created, String branch,
            List<String> tags, List<String> purposes, List<String> tech, List<String> run,
            List<HistorySeed> history, List<String> features,
            String summary, String desc, String repo,
            int views, int likes, int downloads,
            String status, String scope,
            List<FileSeed> files, List<String> readme, List<CommentSeed> comments,
            String rejectReason, String stopReason
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserSeed(String name, String dept, String role) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NotiSeed(Long id, String to, String kind, String title, String sub, boolean read, Long pid) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LogSeed(String at, Long pid, String title, String by, String act, String memo) {}
}
