package com.edu.msa.program;

import com.edu.msa.common.NotFoundException;
import com.edu.msa.common.NotiKind;
import com.edu.msa.common.ProgramStatus;
import com.edu.msa.common.Role;
import com.edu.msa.common.Scope;
import com.edu.msa.notification.NotificationService;
import com.edu.msa.program.domain.Comment;
import com.edu.msa.program.domain.HistoryEntry;
import com.edu.msa.program.domain.Program;
import com.edu.msa.program.domain.ProgramFile;
import com.edu.msa.program.dto.ProgramDtos.CommentRequest;
import com.edu.msa.program.dto.ProgramDtos.CommentResponse;
import com.edu.msa.program.dto.ProgramDtos.CreateProgramRequest;
import com.edu.msa.program.dto.ProgramDtos.FileResponse;
import com.edu.msa.program.dto.ProgramDtos.HistoryResponse;
import com.edu.msa.program.dto.ProgramDtos.ProgramDetailResponse;
import com.edu.msa.program.dto.ProgramDtos.ProgramSummaryResponse;
import com.edu.msa.program.dto.ProgramDtos.ReplyResponse;
import com.edu.msa.program.repository.CommentRepository;
import com.edu.msa.program.repository.ProgramRepository;
import com.edu.msa.user.repository.AppUserRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgramService {

    private static final String DEFAULT_ADMIN = "정우성";

    private final ProgramRepository programs;
    private final CommentRepository comments;
    private final NotificationService notifications;
    private final AppUserRepository users;

    public ProgramService(ProgramRepository programs, CommentRepository comments,
                          NotificationService notifications, AppUserRepository users) {
        this.programs = programs;
        this.comments = comments;
        this.notifications = notifications;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<ProgramSummaryResponse> list(String cat, List<String> purposes, List<String> tech,
                                             String scope, String q, String sort) {
        return programs.findByStatus(ProgramStatus.PUBLIC).stream()
                .filter(p -> cat == null || cat.isBlank() || cat.equals("all") || p.getCat().equals(cat))
                .filter(p -> purposes == null || purposes.isEmpty() || p.getPurposes().containsAll(purposes))
                .filter(p -> tech == null || tech.isEmpty() || p.getTech().containsAll(tech))
                .filter(p -> scope == null || scope.isBlank() || scope.equals("any") || p.getScope().code().equals(scope))
                .filter(p -> matchesQuery(p, q))
                .sorted(sorter(sort))
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProgramSummaryResponse> pending() {
        return programs.findByStatus(ProgramStatus.PENDING).stream()
                .sorted(Comparator.comparing(Program::getCreatedAt).reversed())
                .map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<ProgramSummaryResponse> all() {
        return programs.findAll().stream()
                .sorted(Comparator.comparing(Program::getId))
                .map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public ProgramDetailResponse detail(Long id) {
        Program p = programs.findById(id).orElseThrow(() -> new NotFoundException("프로그램을 찾을 수 없습니다: " + id));
        return toDetail(p);
    }

    @Transactional
    public ProgramDetailResponse create(CreateProgramRequest req) {
        Program p = new Program();
        p.setName(req.name());
        p.setSummary(req.summary());
        p.setDescription(req.desc() != null ? req.desc() : req.summary());
        p.setCat(req.cat());
        p.setOwner(req.owner() != null && !req.owner().isBlank() ? req.owner() : "김도현");
        p.setDept(req.dept() != null && !req.dept().isBlank() ? req.dept() : "행정지원과");
        p.setVersion(req.ver() != null && !req.ver().isBlank() ? req.ver() : "1.0.0");
        p.setRepoUrl(req.repo());
        p.setBranch(req.branch() != null && !req.branch().isBlank() ? req.branch() : "main");
        p.setStatus(ProgramStatus.PENDING);
        p.setScope(req.scope() != null ? Scope.from(req.scope()) : Scope.ALL);
        LocalDate today = LocalDate.now();
        p.setCreatedAt(today);
        p.setUpdatedAt(today);
        if (req.tags() != null) { p.getTags().addAll(req.tags()); p.getTech().addAll(req.tags()); }
        if (req.purposes() != null) p.getPurposes().addAll(req.purposes());
        p.getRun().addAll(req.run() != null && !req.run().isEmpty() ? req.run() : List.of("gitea"));
        p.getHistory().add(new HistoryEntry(p.getVersion(), today.toString(), "최초 등록 요청"));
        if (req.readme() != null && !req.readme().isBlank()) {
            p.getReadme().addAll(List.of(req.readme().split("\n")));
        } else {
            p.getReadme().addAll(List.of("## 개요", req.summary()));
        }
        Program saved = programs.save(p);

        notifications.push(adminName(), NotiKind.SUBMIT,
                "「" + saved.getName() + "」 등록 요청이 접수되었습니다.",
                saved.getOwner() + " · " + saved.getDept() + " · " + today,
                saved.getId());

        return toDetail(saved);
    }

    @Transactional
    public CommentResponse addComment(Long id, CommentRequest req) {
        Program p = programs.findById(id).orElseThrow(() -> new NotFoundException("프로그램을 찾을 수 없습니다: " + id));
        Comment c = new Comment(p.getId(), req.user(), req.dept() != null ? req.dept() : "", LocalDate.now().toString(), req.body());
        Comment saved = comments.save(c);
        return toCommentResponse(saved);
    }

    private String adminName() {
        return users.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN)
                .map(u -> u.getName())
                .findFirst().orElse(DEFAULT_ADMIN);
    }

    private boolean matchesQuery(Program p, String q) {
        if (q == null || q.isBlank()) return true;
        String needle = q.trim().toLowerCase();
        StringBuilder hay = new StringBuilder()
                .append(p.getName()).append(' ').append(p.getSummary()).append(' ')
                .append(p.getDescription() == null ? "" : p.getDescription()).append(' ')
                .append(String.join(" ", p.getTags())).append(' ')
                .append(String.join(" ", p.getTech()));
        return hay.toString().toLowerCase().contains(needle);
    }

    private Comparator<Program> sorter(String sort) {
        if ("popular".equals(sort)) return Comparator.comparingInt(Program::getViews).reversed();
        if ("downloads".equals(sort)) return Comparator.comparingInt(Program::getDownloads).reversed();
        return Comparator.comparing(Program::getUpdatedAt).reversed();
    }

    private String repoName(String repo) {
        if (repo == null) return "";
        String[] parts = repo.replaceAll("/+$", "").split("/");
        return parts.length > 0 ? parts[parts.length - 1] : repo;
    }

    private ProgramSummaryResponse toSummary(Program p) {
        return new ProgramSummaryResponse(
                p.getId(), p.getName(), p.getSlug(), p.getCat(), p.getOwner(), p.getDept(),
                p.getVersion(), str(p.getUpdatedAt()), str(p.getCreatedAt()), p.getBranch(),
                p.getRepoUrl(), repoName(p.getRepoUrl()), p.getSummary(),
                List.copyOf(p.getTags()), List.copyOf(p.getPurposes()), List.copyOf(p.getTech()), List.copyOf(p.getRun()),
                p.getViews(), p.getLikes(), p.getDownloads(), p.getStatus(), p.getScope());
    }

    private ProgramDetailResponse toDetail(Program p) {
        List<CommentResponse> commentList = comments.findByProgramIdOrderByIdAsc(p.getId()).stream()
                .map(this::toCommentResponse).toList();
        List<HistoryResponse> hist = p.getHistory().stream()
                .map(h -> new HistoryResponse(h.getVer(), h.getDate(), h.getLog())).toList();
        List<FileResponse> files = p.getFiles().stream()
                .map(f -> new FileResponse(f.getName(), f.getSize(), f.getType())).toList();
        return new ProgramDetailResponse(
                p.getId(), p.getName(), p.getSlug(), p.getCat(), p.getOwner(), p.getDept(),
                p.getVersion(), str(p.getUpdatedAt()), str(p.getCreatedAt()), p.getBranch(),
                p.getRepoUrl(), repoName(p.getRepoUrl()), p.getSummary(), p.getDescription(),
                List.copyOf(p.getTags()), List.copyOf(p.getPurposes()), List.copyOf(p.getTech()), List.copyOf(p.getRun()),
                p.getViews(), p.getLikes(), p.getDownloads(), p.getStatus(), p.getScope(),
                p.getRejectReason(), p.getStopReason(),
                List.copyOf(p.getFeatures()), List.copyOf(p.getReadme()), hist, files, commentList);
    }

    private CommentResponse toCommentResponse(Comment c) {
        ReplyResponse reply = c.hasReply()
                ? new ReplyResponse(c.getReplyUser(), c.getReplyDept(), c.getReplyTime(), c.getReplyBody())
                : null;
        return new CommentResponse(c.getId(), c.getUser(), c.getDept(), c.getTime(), c.getBody(), reply);
    }

    private String str(LocalDate d) {
        return d == null ? null : d.toString();
    }
}
