package com.edu.msa.bootstrap;

import com.edu.msa.bootstrap.SeedModels.CommentSeed;
import com.edu.msa.bootstrap.SeedModels.FileSeed;
import com.edu.msa.bootstrap.SeedModels.HistorySeed;
import com.edu.msa.bootstrap.SeedModels.LogSeed;
import com.edu.msa.bootstrap.SeedModels.NotiSeed;
import com.edu.msa.bootstrap.SeedModels.ProgramSeed;
import com.edu.msa.bootstrap.SeedModels.UserSeed;
import com.edu.msa.common.NotiKind;
import com.edu.msa.common.ProgramStatus;
import com.edu.msa.common.ReviewAction;
import com.edu.msa.common.Role;
import com.edu.msa.common.Scope;
import com.edu.msa.notification.domain.Notification;
import com.edu.msa.notification.repository.NotificationRepository;
import com.edu.msa.program.domain.Comment;
import com.edu.msa.program.domain.HistoryEntry;
import com.edu.msa.program.domain.Program;
import com.edu.msa.program.domain.ProgramFile;
import com.edu.msa.program.repository.CommentRepository;
import com.edu.msa.program.repository.ProgramRepository;
import com.edu.msa.review.domain.ReviewLog;
import com.edu.msa.review.repository.ReviewLogRepository;
import com.edu.msa.user.domain.AppUser;
import com.edu.msa.user.repository.AppUserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 최초 실행 시(빈 DB, edu.seed=true) 목업 데이터를 적재한다. */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final ProgramRepository programs;
    private final CommentRepository comments;
    private final AppUserRepository users;
    private final ReviewLogRepository reviewLogs;
    private final NotificationRepository notifications;
    private final ObjectMapper mapper;
    private final boolean seedEnabled;

    public DataSeeder(ProgramRepository programs, CommentRepository comments, AppUserRepository users,
                      ReviewLogRepository reviewLogs, NotificationRepository notifications,
                      ObjectMapper mapper, @Value("${edu.seed:true}") boolean seedEnabled) {
        this.programs = programs;
        this.comments = comments;
        this.users = users;
        this.reviewLogs = reviewLogs;
        this.notifications = notifications;
        this.mapper = mapper;
        this.seedEnabled = seedEnabled;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (!seedEnabled) return;
        if (programs.count() > 0) {
            log.info("시드 건너뜀: 프로그램 {}건 존재", programs.count());
            return;
        }
        log.info("목업 데이터 시드 시작");

        for (UserSeed u : read("seed/users.json", new TypeReference<List<UserSeed>>() {})) {
            users.save(new AppUser(u.name(), u.dept(), Role.from(u.role())));
        }

        for (ProgramSeed s : read("seed/programs.json", new TypeReference<List<ProgramSeed>>() {})) {
            Program p = toProgram(s);
            Program saved = programs.save(p);
            if (s.comments() != null) {
                for (CommentSeed c : s.comments()) {
                    Comment cm = new Comment(saved.getId(), c.user(), c.dept(), c.time(), c.body());
                    if (c.reply() != null) {
                        cm.setReply(c.reply().user(), c.reply().dept(), c.reply().time(), c.reply().body());
                    }
                    comments.save(cm);
                }
            }
        }

        for (LogSeed l : read("seed/reviewLogs.json", new TypeReference<List<LogSeed>>() {})) {
            reviewLogs.save(new ReviewLog(l.at(), l.pid(), l.title(), l.by(), ReviewAction.from(l.act()), l.memo()));
        }

        for (NotiSeed n : read("seed/notifications.json", new TypeReference<List<NotiSeed>>() {})) {
            notifications.save(new Notification(n.to(), NotiKind.from(n.kind()), n.title(), n.sub(), n.pid(), n.read()));
        }

        log.info("시드 완료: 프로그램 {}건, 사용자 {}명", programs.count(), users.count());
    }

    private Program toProgram(ProgramSeed s) {
        Program p = new Program();
        p.setName(s.name());
        p.setSlug(slugFrom(s.repo(), s.id()));
        p.setCat(s.cat());
        p.setOwner(s.owner());
        p.setDept(s.dept());
        p.setVersion(s.ver());
        p.setSummary(s.summary());
        p.setDescription(s.desc());
        p.setRepoUrl(s.repo());
        p.setBranch(s.branch() != null ? s.branch() : "main");
        p.setStatus(ProgramStatus.from(s.status()));
        p.setScope(Scope.from(s.scope()));
        p.setViews(s.views());
        p.setLikes(s.likes());
        p.setDownloads(s.downloads());
        p.setCreatedAt(LocalDate.parse(s.created()));
        p.setUpdatedAt(LocalDate.parse(s.updated()));
        p.setRejectReason(s.rejectReason());
        p.setStopReason(s.stopReason());
        if (s.tags() != null) p.getTags().addAll(s.tags());
        if (s.tech() != null) p.getTech().addAll(s.tech());
        if (s.purposes() != null) p.getPurposes().addAll(s.purposes());
        if (s.run() != null) p.getRun().addAll(s.run());
        if (s.features() != null) p.getFeatures().addAll(s.features());
        if (s.readme() != null) p.getReadme().addAll(s.readme());
        if (s.history() != null) {
            for (HistorySeed h : s.history()) p.getHistory().add(new HistoryEntry(h.ver(), h.date(), h.log()));
        }
        if (s.files() != null) {
            for (FileSeed f : s.files()) p.getFiles().add(new ProgramFile(f.name(), f.size(), f.type()));
        }
        return p;
    }

    private String slugFrom(String repo, Long id) {
        if (repo == null) return "svc-" + id;
        String[] parts = repo.replaceAll("/+$", "").split("/");
        String last = parts.length > 0 ? parts[parts.length - 1] : ("svc-" + id);
        return last.toLowerCase();
    }

    private <T> List<T> read(String path, TypeReference<List<T>> type) throws Exception {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return mapper.readValue(in, type);
        }
    }
}
