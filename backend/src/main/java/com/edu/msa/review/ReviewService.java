package com.edu.msa.review;

import com.edu.msa.common.NotFoundException;
import com.edu.msa.common.NotiKind;
import com.edu.msa.common.ProgramStatus;
import com.edu.msa.common.ReviewAction;
import com.edu.msa.deploy.DeployJobService;
import com.edu.msa.deploy.DeployProperties;
import com.edu.msa.notification.NotificationService;
import com.edu.msa.program.domain.Program;
import com.edu.msa.program.repository.ProgramRepository;
import com.edu.msa.review.domain.ReviewLog;
import com.edu.msa.review.dto.ReviewDtos.ReviewLogResponse;
import com.edu.msa.review.repository.ReviewLogRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {

    private static final String DEFAULT_ACTOR = "정우성";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final ProgramRepository programs;
    private final ReviewLogRepository logs;
    private final NotificationService notifications;
    private final DeployJobService deployJobs;
    private final DeployProperties deployProps;

    public ReviewService(ProgramRepository programs, ReviewLogRepository logs, NotificationService notifications,
                         DeployJobService deployJobs, DeployProperties deployProps) {
        this.programs = programs;
        this.logs = logs;
        this.notifications = notifications;
        this.deployJobs = deployJobs;
        this.deployProps = deployProps;
    }

    @Transactional
    public void review(Long id, ReviewAction action, String memo, String actor) {
        Program p = programs.findById(id).orElseThrow(() -> new NotFoundException("프로그램을 찾을 수 없습니다: " + id));
        String who = (actor != null && !actor.isBlank()) ? actor : DEFAULT_ACTOR;
        String note = memo != null ? memo : "";

        // 승인 + 자동배포가 켜져 있고 레포 주소가 있으면: 공개는 배포 작업 성공 시 파이프라인이 처리한다.
        boolean autoDeploy = action == ReviewAction.APPROVE
                && deployProps.autoOnApprove()
                && p.getRepoUrl() != null && !p.getRepoUrl().isBlank();

        switch (action) {
            case APPROVE -> {
                p.setRejectReason(null);
                if (!autoDeploy) {
                    p.setStatus(ProgramStatus.PUBLIC);   // 자동배포 미사용 시 즉시 공개
                }
            }
            case REJECT -> { p.setStatus(ProgramStatus.REJECTED); p.setRejectReason(note); }
            case STOP -> { p.setStatus(ProgramStatus.STOPPED); p.setStopReason(note); }
            case RESUME -> { p.setStatus(ProgramStatus.PUBLIC); p.setStopReason(null); }
        }
        p.setUpdatedAt(LocalDate.now());

        logs.save(new ReviewLog(now(), p.getId(), p.getName(), who, action, note));

        NotiKind kind = switch (action) {
            case APPROVE -> NotiKind.APPROVE;
            case REJECT -> NotiKind.REJECT;
            default -> NotiKind.SUBMIT;
        };
        String label = switch (action) {
            case APPROVE -> autoDeploy ? "승인되어 배포 대기열에 등록되었습니다." : "승인되어 공개되었습니다.";
            case REJECT -> "반려되었습니다.";
            case STOP -> "공개가 중지되었습니다.";
            case RESUME -> "다시 공개되었습니다.";
        };
        notifications.push(p.getOwner(), kind,
                "「" + p.getName() + "」 등록 요청이 " + label,
                "운영 관리자 " + who + " · " + LocalDate.now(),
                p.getId());

        // 자동배포: 배포 작업 큐에 적재(워커가 처리, 성공 시 프로그램 public 전환)
        if (autoDeploy) {
            deployJobs.enqueue(id, p.getRepoUrl(), p.getBranch(), who);
        }
    }

    @Transactional(readOnly = true)
    public List<ReviewLogResponse> logs() {
        return logs.findAllByOrderByIdDesc().stream()
                .map(l -> new ReviewLogResponse(l.getAt(), l.getProgramId(), l.getTitle(), l.getBy(), l.getAction(), l.getMemo()))
                .toList();
    }

    private String now() {
        return LocalDateTime.now(SEOUL).format(STAMP);
    }
}
