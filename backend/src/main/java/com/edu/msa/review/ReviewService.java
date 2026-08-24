package com.edu.msa.review;

import com.edu.msa.common.NotFoundException;
import com.edu.msa.common.NotiKind;
import com.edu.msa.common.ProgramStatus;
import com.edu.msa.common.ReviewAction;
import com.edu.msa.deploy.DeployProperties;
import com.edu.msa.deploy.DeploymentService;
import com.edu.msa.deploy.dto.DeployDtos.DeployRequest;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ReviewService {

    private static final String DEFAULT_ACTOR = "정우성";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final ProgramRepository programs;
    private final ReviewLogRepository logs;
    private final NotificationService notifications;
    private final DeploymentService deploymentService;
    private final DeployProperties deployProps;
    // 승인 시 자동 배포(clone→build→run/apply)는 시간이 걸리므로 백그라운드로 실행
    private final ExecutorService deployExecutor = Executors.newFixedThreadPool(2);

    public ReviewService(ProgramRepository programs, ReviewLogRepository logs, NotificationService notifications,
                         DeploymentService deploymentService, DeployProperties deployProps) {
        this.programs = programs;
        this.logs = logs;
        this.notifications = notifications;
        this.deploymentService = deploymentService;
        this.deployProps = deployProps;
    }

    @Transactional
    public void review(Long id, ReviewAction action, String memo, String actor) {
        Program p = programs.findById(id).orElseThrow(() -> new NotFoundException("프로그램을 찾을 수 없습니다: " + id));
        String who = (actor != null && !actor.isBlank()) ? actor : DEFAULT_ACTOR;
        String note = memo != null ? memo : "";

        // 승인 + 자동배포가 켜져 있고 레포 주소가 있으면: 공개는 배포 성공 시 파이프라인이 처리한다.
        boolean autoDeploy = action == ReviewAction.APPROVE
                && deployProps.autoOnApprove()
                && p.getRepoUrl() != null && !p.getRepoUrl().isBlank();

        switch (action) {
            case APPROVE -> {
                p.setRejectReason(null);
                if (!autoDeploy) {
                    p.setStatus(ProgramStatus.PUBLIC);   // 자동배포 미사용 시 즉시 공개(기존 동작)
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
            case APPROVE -> autoDeploy ? "승인되어 배포를 시작합니다." : "승인되어 공개되었습니다.";
            case REJECT -> "반려되었습니다.";
            case STOP -> "공개가 중지되었습니다.";
            case RESUME -> "다시 공개되었습니다.";
        };
        notifications.push(p.getOwner(), kind,
                "「" + p.getName() + "」 등록 요청이 " + label,
                "운영 관리자 " + who + " · " + LocalDate.now(),
                p.getId());

        if (autoDeploy) {
            final DeployRequest req = new DeployRequest(id, p.getRepoUrl(), p.getBranch(), who);
            // 승인 트랜잭션이 커밋된 뒤 백그라운드로 배포 실행 (deploy가 성공 시 프로그램을 public으로 전환)
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deployExecutor.submit(() -> deploymentService.deploy(req));
                }
            });
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
