package com.edu.msa.program.dto;

import com.edu.msa.common.ProgramStatus;
import com.edu.msa.common.Scope;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/** 프로그램 도메인 요청/응답 DTO 모음. */
public final class ProgramDtos {
    private ProgramDtos() {}

    public record HistoryResponse(String ver, String date, String log) {}

    public record FileResponse(String name, String size, String type) {}

    public record ReplyResponse(String user, String dept, String time, String body) {}

    public record CommentResponse(Long id, String user, String dept, String time, String body, ReplyResponse reply) {}

    public record ProgramSummaryResponse(
            Long id, String name, String slug, String cat, String owner, String dept,
            String ver, String updated, String created, String branch,
            String repo, String repoName, String summary,
            List<String> tags, List<String> purposes, List<String> tech, List<String> run,
            int views, int likes, int downloads,
            ProgramStatus status, Scope scope
    ) {}

    public record ProgramDetailResponse(
            Long id, String name, String slug, String cat, String owner, String dept,
            String ver, String updated, String created, String branch,
            String repo, String repoName, String summary, String desc,
            List<String> tags, List<String> purposes, List<String> tech, List<String> run,
            int views, int likes, int downloads,
            ProgramStatus status, Scope scope,
            String rejectReason, String stopReason,
            List<String> features, List<String> readme,
            List<HistoryResponse> history, List<FileResponse> files, List<CommentResponse> comments
    ) {}

    public record CreateProgramRequest(
            @NotBlank String name,
            @NotBlank String summary,
            String desc,
            @NotBlank String cat,
            String owner,
            String dept,
            String ver,
            @NotBlank String repo,
            String branch,
            List<String> tags,
            List<String> purposes,
            List<String> run,
            String scope,
            String readme
    ) {}

    public record CommentRequest(
            @NotBlank String user,
            String dept,
            @NotBlank String body
    ) {}
}
