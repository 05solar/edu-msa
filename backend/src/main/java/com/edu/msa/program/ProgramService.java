package com.edu.msa.program;

import com.edu.msa.cache.CacheConfig;
import com.edu.msa.cache.CatalogCacheEvictor;
import com.edu.msa.common.NotFoundException;
import com.edu.msa.common.NotiKind;
import com.edu.msa.common.PageResponse;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgramService {

    private static final String DEFAULT_ADMIN = "정우성";

    private final ProgramRepository programs;
    private final CommentRepository comments;
    private final NotificationService notifications;
    private final AppUserRepository users;
    private final CatalogCacheEvictor cacheEvictor;

    public ProgramService(ProgramRepository programs, CommentRepository comments,
                          NotificationService notifications, AppUserRepository users,
                          CatalogCacheEvictor cacheEvictor) {
        this.programs = programs;
        this.comments = comments;
        this.notifications = notifications;
        this.users = users;
        this.cacheEvictor = cacheEvictor;
    }

    /**
     * 공개 카탈로그 목록 — 필터·검색·정렬을 전부 DB 쿼리로 수행하고 페이지 단위로 반환한다.
     * 컬렉션 조건은 EXISTS 서브쿼리(ProgramSpecs)라 페이지 행 수가 왜곡되지 않는다.
     * 결과는 필터 조합을 키로 짧게 캐시된다(TTL edu.cache.list-ttl-seconds, 변경 시 즉시 무효화).
     */
    @Cacheable(cacheNames = CacheConfig.CATALOG_LIST,
            key = "#cat + '|' + #scope + '|' + #q + '|' + #sort + '|' + #page + '|' + #size"
                    + " + '|' + #purposes + '|' + #tech")
    @Transactional(readOnly = true)
    public PageResponse<ProgramSummaryResponse> list(String cat, List<String> purposes, List<String> tech,
                                                     String scope, String q, String sort, int page, int size) {
        Specification<Program> spec = Stream.of(
                        ProgramSpecs.hasStatus(ProgramStatus.PUBLIC),
                        ProgramSpecs.hasCat(cat),
                        ProgramSpecs.hasScope(scope),
                        ProgramSpecs.containsAll("purposes", purposes),
                        ProgramSpecs.containsAll("tech", tech),
                        ProgramSpecs.matchesQuery(q))
                .filter(Objects::nonNull)
                .reduce(Specification::and)
                .orElseThrow();
        Page<Program> result = programs.findAll(spec, pageOf(page, size, sortOf(sort)));
        return PageResponse.of(result.map(this::toSummary));
    }

    /** 카탈로그 사이드바용 분야별 공개 프로그램 개수(GROUP BY 집계) — 짧은 TTL 캐시. */
    @Cacheable(cacheNames = CacheConfig.CATALOG_COUNTS, key = "'all'")
    @Transactional(readOnly = true)
    public Map<String, Long> publicCountsByCat() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : programs.countByCatForStatus(ProgramStatus.PUBLIC)) {
            counts.put((String) row[0], (Long) row[1]);
        }
        return counts;
    }

    /**
     * 등록자 본인 재배포 — 레포를 갱신한 소유자가 새 버전으로 다시 배포를 요청한다.
     * 소유자 검증을 서버에서 강제하고(ADMIN 은 전체 허용), 배포 대상 레포는 저장된
     * 값만 사용한다. 버전이 바뀌면 업데이트 내역(history)에 기록을 남긴다.
     */
    @Transactional
    public Program requestRedeploy(Long id, String requester, boolean admin, String version, String note) {
        Program p = programs.findById(id)
                .orElseThrow(() -> new NotFoundException("프로그램을 찾을 수 없습니다: " + id));
        if (!admin && !p.getOwner().equals(requester)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "본인이 등록한 프로그램만 재배포할 수 있습니다.");
        }
        if (p.getStatus() != ProgramStatus.PUBLIC) {
            throw new IllegalArgumentException(
                    "공개 중인 프로그램만 재배포할 수 있습니다. (현재 상태: " + p.getStatus().code() + ")");
        }
        LocalDate today = LocalDate.now();
        if (version != null && !version.isBlank()) {
            p.setVersion(version.trim());
        }
        p.getHistory().add(new HistoryEntry(p.getVersion(), today.toString(),
                note != null && !note.isBlank() ? note.trim() : "레포 업데이트 재배포"));
        p.setUpdatedAt(today);
        cacheEvictor.evictAll();   // 버전·수정일 변경이 목록 정렬에 반영되도록
        return p;
    }

    /**
     * 삭제 권한 검증 — 소유자 본인 또는 운영 관리자(ADMIN)만 삭제할 수 있다.
     * 배포 흔적 정리(DeploymentService.removeFor)보다 먼저 호출해 권한 없는 삭제 시도를 차단한다.
     */
    @Transactional(readOnly = true)
    public Program requireDeletable(Long id, String requester, boolean admin) {
        Program p = programs.findById(id)
                .orElseThrow(() -> new NotFoundException("프로그램을 찾을 수 없습니다: " + id));
        if (!admin && !p.getOwner().equals(requester)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "본인이 등록한 프로그램만 삭제할 수 있습니다.");
        }
        return p;
    }

    /** 프로그램과 부속 데이터(의견·알림)를 삭제한다. 권한 검증과 배포 정리는 호출부가 선행한다. */
    @Transactional
    public void delete(Long id) {
        comments.deleteByProgramId(id);
        notifications.deleteForProgram(id);
        programs.deleteById(id);
        cacheEvictor.evictAll();
    }

    @Transactional(readOnly = true)
    public PageResponse<ProgramSummaryResponse> pending(int page, int size) {
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        return PageResponse.of(programs.findByStatus(ProgramStatus.PENDING, pageOf(page, size, sort))
                .map(this::toSummary));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProgramSummaryResponse> all(int page, int size) {
        return PageResponse.of(programs.findAll(pageOf(page, size, Sort.by(Sort.Order.asc("id"))))
                .map(this::toSummary));
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

        cacheEvictor.evictAll();
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

    /** 정렬 화이트리스트 — 요청값을 엔티티 정렬로 직접 쓰지 않는다(동점은 id 로 안정 정렬). */
    private Sort sortOf(String sort) {
        if ("popular".equals(sort)) return Sort.by(Sort.Order.desc("views"), Sort.Order.desc("id"));
        if ("downloads".equals(sort)) return Sort.by(Sort.Order.desc("downloads"), Sort.Order.desc("id"));
        return Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));
    }

    /** 페이지 파라미터 방어값(page ≥ 0, 1 ≤ size ≤ 100). */
    private Pageable pageOf(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort);
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
