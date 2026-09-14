package com.edu.msa.deploy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.edu.msa.common.DeployJobStatus;
import com.edu.msa.deploy.domain.DeployJob;
import com.edu.msa.deploy.dto.DeployDtos.DeployJobResponse;
import com.edu.msa.deploy.repository.DeployJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * P0-2 — 동일 프로그램 중복 배포(enqueue) 멱등성.
 *
 * 더블클릭·승인 API 중복 호출·webhook replay·network retry 가 반복돼도
 * 같은 프로그램의 active(QUEUED/RUNNING) 작업은 1개만 존재해야 한다.
 * 빠른 경로는 기존 active 작업 반환(멱등), 동시 경쟁의 최종 심판은
 * MariaDB 생성 컬럼 유니크(uq_deploy_jobs_active_program)가 담당한다
 * (H2 는 생성 컬럼 유니크 미지원 — DB 경쟁 경로는 MariaDbDeployConcurrencyIT 가 실측).
 */
@SpringBootTest
@ActiveProfiles("test")
class DeployJobIdempotencyTest {

    @Autowired private DeployJobService jobs;
    @Autowired private DeployJobRepository repo;

    @Test
    void 같은_프로그램의_반복_enqueue는_기존_active_작업을_반환한다() {
        long programId = 9001L;
        DeployJobResponse first = jobs.enqueue(programId, "https://github.com/t/r", "main", "u1");
        // 더블클릭/승인 중복/webhook replay 시뮬레이션 — 모두 같은 작업이어야 한다
        DeployJobResponse dup1 = jobs.enqueue(programId, "https://github.com/t/r", "main", "u2");
        DeployJobResponse dup2 = jobs.enqueue(programId, "https://github.com/t/r", "main", "gitea-webhook");
        assertEquals(first.id(), dup1.id(), "중복 enqueue 는 기존 active 작업을 반환해야 한다");
        assertEquals(first.id(), dup2.id(), "webhook replay 도 기존 active 작업을 반환해야 한다");
        assertEquals(1, repo.findAll().stream()
                        .filter(j -> Long.valueOf(programId).equals(j.getProgramId()))
                        .filter(j -> j.getStatus() == DeployJobStatus.QUEUED
                                || j.getStatus() == DeployJobStatus.RUNNING)
                        .count(),
                "active 작업은 1개만 존재해야 한다");
    }

    @Test
    void RUNNING_중에도_중복_enqueue는_흡수된다() {
        long programId = 9002L;
        DeployJobResponse first = jobs.enqueue(programId, "https://github.com/t/r2", "main", "u1");
        DeployJob claimed = jobs.claimNext();
        assertEquals(first.id(), claimed.getId());
        assertEquals(DeployJobStatus.RUNNING, claimed.getStatus());
        DeployJobResponse dup = jobs.enqueue(programId, "https://github.com/t/r2", "main", "u1");
        assertEquals(first.id(), dup.id(), "RUNNING 중 재요청도 기존 작업 반환");
    }

    @Test
    void 종료된_작업_이후의_enqueue는_새_작업을_만든다() {
        long programId = 9003L;
        DeployJobResponse first = jobs.enqueue(programId, "https://github.com/t/r3", "main", "u1");
        DeployJob claimed = jobs.claimNext();
        jobs.complete(claimed.getId(), true, 1L, null);
        DeployJobResponse second = jobs.enqueue(programId, "https://github.com/t/r3", "main", "u1");
        assertNotEquals(first.id(), second.id(), "완료 후 재배포는 새 작업이어야 한다");
    }

    @Test
    void 영구_실패는_재시도_큐에_들어가지_않고_terminal로_끝난다() {
        long programId = 9004L;
        jobs.enqueue(programId, "https://github.com/t/r4", "main", "u1");
        DeployJob claimed = jobs.claimNext();
        jobs.completeTerminal(claimed.getId(), "slug 예약 충돌(영구 오류)");
        DeployJob after = repo.findById(claimed.getId()).orElseThrow();
        assertEquals(DeployJobStatus.FAILED, after.getStatus(),
                "영구 오류는 attempts 잔여와 무관하게 FAILED terminal 이어야 한다");
        assertEquals(1, after.getAttempts(), "재시도 없이 1회 시도로 끝나야 한다");
    }
}
