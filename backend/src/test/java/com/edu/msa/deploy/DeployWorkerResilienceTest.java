package com.edu.msa.deploy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edu.msa.common.DeployJobStatus;
import com.edu.msa.common.DeploymentStatus;
import com.edu.msa.deploy.domain.DeployJob;
import com.edu.msa.deploy.dto.DeployDtos.DeploymentResponse;
import com.edu.msa.deploy.repository.DeployJobRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배포 큐 워커의 복원력 검증 — 재시도 백오프, 워커 강제 종료 시 작업 회수(유실 방지),
 * tick 당 연속 처리(drain). claim 은 기존 SKIP LOCKED 경로를 그대로 사용한다.
 */
@SpringBootTest(properties = {
        "edu.deploy.worker.enabled=true",          // 워커 빈 활성(스케줄은 poll 간격이라 간섭 없음)
        "edu.deploy.worker.poll-ms=3600000",       // 자동 tick 이 테스트를 방해하지 않게 1시간
        "edu.deploy.worker.retry-backoff-seconds=30",
        "spring.datasource.url=jdbc:h2:mem:workerdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
})
@ActiveProfiles("test")
class DeployWorkerResilienceTest {

    @Autowired private DeployWorker worker;
    @Autowired private DeployJobService jobs;
    @Autowired private DeployJobRepository repo;
    @PersistenceContext private EntityManager em;

    @MockBean private DeploymentService deployments;

    private DeploymentResponse response(DeploymentStatus status) {
        return new DeploymentResponse(1L, null, "s", "n", status, null, null, "simulate", null, null, Instant.now());
    }

    @Test
    @Transactional
    void 실패한_작업은_지수_백오프와_함께_재큐잉되고_백오프_중에는_claim_되지_않는다() {
        Long id = jobs.enqueue(null, "sample://retry-case", "main", "t").id();

        DeployJob claimed = jobs.claimNext();
        assertNotNull(claimed);
        assertEquals(id, claimed.getId());

        jobs.complete(id, false, null, "clone 실패");   // 시도 1 실패 → 백오프 30s
        DeployJob j = repo.findById(id).orElseThrow();
        assertEquals(DeployJobStatus.QUEUED, j.getStatus());
        assertTrue(j.getNextAttemptAt().isAfter(Instant.now().plusSeconds(20)),
                "재시도는 즉시가 아니라 백오프 후여야 한다");

        assertNull(jobs.claimNext(), "백오프 중에는 claim 대상이 아니어야 한다");

        // 백오프 시각이 지나면 다시 claim 된다
        em.createQuery("update DeployJob j set j.nextAttemptAt = :past where j.id = :id")
                .setParameter("past", Instant.now().minusSeconds(1)).setParameter("id", id).executeUpdate();
        em.clear();
        DeployJob reclaimed = jobs.claimNext();
        assertNotNull(reclaimed);
        assertEquals(id, reclaimed.getId());
        assertEquals(2, reclaimed.getAttempts());

        jobs.complete(id, false, null, "또 실패");      // 시도 2 = maxAttempts → FAILED
        assertEquals(DeployJobStatus.FAILED, repo.findById(id).orElseThrow().getStatus());
    }

    @Test
    @Transactional
    void 워커_강제종료로_방치된_RUNNING_작업은_회수되어_유실되지_않는다() {
        Long id = jobs.enqueue(null, "sample://crash-case", "main", "t").id();
        assertNotNull(jobs.claimNext());   // RUNNING 전이 = 워커가 잡은 상태

        // 워커 프로세스가 죽어 complete() 가 영원히 호출되지 않은 상황 모사(갱신 시각을 과거로)
        em.createQuery("update DeployJob j set j.updatedAt = :old where j.id = :id")
                .setParameter("old", Instant.now().minusSeconds(3600)).setParameter("id", id).executeUpdate();
        em.clear();

        assertEquals(1, jobs.requeueStale(), "방치된 RUNNING 이 회수되어야 한다");
        DeployJob j = repo.findById(id).orElseThrow();
        assertEquals(DeployJobStatus.QUEUED, j.getStatus());

        DeployJob reclaimed = jobs.claimNext();   // 다른(새) 워커가 다시 잡는다 — 유실 없음
        assertNotNull(reclaimed);
        assertEquals(id, reclaimed.getId());
    }

    @Test
    @Transactional
    void tick_한_번에_큐가_빌_때까지_연속_처리한다() {
        when(deployments.deploy(any())).thenReturn(response(DeploymentStatus.RUNNING));
        jobs.enqueue(null, "sample://drain-1", "main", "t");
        jobs.enqueue(null, "sample://drain-2", "main", "t");
        jobs.enqueue(null, "sample://drain-3", "main", "t");

        worker.tick();

        verify(deployments, times(3)).deploy(any());
        assertEquals(3, repo.countByStatus(DeployJobStatus.DONE), "폴링 1회에 3건 모두 처리되어야 한다");
        assertEquals(0, repo.countByStatus(DeployJobStatus.QUEUED));
    }
}
