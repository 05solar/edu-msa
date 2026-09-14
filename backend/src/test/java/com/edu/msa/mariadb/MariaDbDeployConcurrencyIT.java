package com.edu.msa.mariadb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edu.msa.common.DeployJobStatus;
import com.edu.msa.deploy.DeployJobService;
import com.edu.msa.deploy.SlugClaims;
import com.edu.msa.deploy.domain.DeployJob;
import com.edu.msa.deploy.repository.DeployJobRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * P0-2 배포 동시성 제약의 MariaDB 실측 검증(Testcontainers — H2 로는 재현 불가).
 *
 * 검증 대상:
 *  1) V3 생성 컬럼 유니크(uq_deploy_jobs_active_program) — 같은 프로그램의 active
 *     (QUEUED/RUNNING) 작업 동시 INSERT 경쟁에서 정확히 하나만 승자가 된다.
 *  2) 상태 전이 — DONE/FAILED 로 빠진 프로그램은 새 active 작업을 다시 받을 수 있다.
 *  3) FOR UPDATE SKIP LOCKED(claim) — 두 워커가 같은 작업을 동시에 가져가지 않는다.
 *  4) 제약 위반이 Spring 예외 변환(DataIntegrityViolationException)을 거쳐 기존
 *     서비스 로직(SlugClaims)에서 정상 처리된다.
 *
 * Flyway 전체 마이그레이션(vendor/mariadb) + ddl-auto validate + 시드 INSERT 위에서
 * 돌므로 clean install 경로 검증을 겸한다(MariaDbSchemaMigrationIT 와 동일 전제).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.cache.type=none",
        // 시드까지 켠다 — Flyway 스키마 위에서 목업 INSERT(한글 포함)가 실제로 성공해야
        // 컨텍스트가 뜬다(clean install 검증 겸용). deploy_jobs 테스트와는 간섭 없음.
        "edu.seed=true",
        "edu.deploy.worker.enabled=false",
        "edu.jwt.secret=test-secret-key-for-edu-msa-backend-only-32bytes+",
})
class MariaDbDeployConcurrencyIT {

    @Container
    static final MariaDBContainer<?> DB = new MariaDBContainer<>("mariadb:11.4")
            .withDatabaseName("edumsa")
            .withUsername("edumsa")
            .withPassword("edumsa")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> DB.getJdbcUrl() + "?timezone=UTC");
        r.add("spring.datasource.username", DB::getUsername);
        r.add("spring.datasource.password", DB::getPassword);
    }

    @Autowired private DeployJobRepository jobs;
    @Autowired private DeployJobService jobService;
    @Autowired private SlugClaims slugClaims;
    @Autowired private PlatformTransactionManager txm;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM deploy_jobs");
        jdbc.update("DELETE FROM slug_claims");
    }

    private TransactionTemplate newTx() {
        TransactionTemplate t = new TransactionTemplate(txm);
        t.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return t;
    }

    private DeployJob insertJob(long programId, DeployJobStatus status) {
        return newTx().execute(s -> {
            DeployJob j = new DeployJob(programId, "https://gitea.example/r.git", "main", "it");
            j.setStatus(status);
            return jobs.saveAndFlush(j);
        });
    }

    // ---------- Case 1 · 같은 프로그램 동시 QUEUED 2건: 정확히 1건만 성공 ----------
    @Test
    void 동시_INSERT_경쟁에서_정확히_하나만_승자가_된다() throws Exception {
        final long pid = 101L;
        final int racers = 2;
        CyclicBarrier start = new CyclicBarrier(racers);
        AtomicInteger wins = new AtomicInteger();
        AtomicInteger constraintLosses = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < racers; i++) {
            futures.add(pool.submit(() -> {
                try {
                    start.await(10, TimeUnit.SECONDS);
                    insertJob(pid, DeployJobStatus.QUEUED);
                    wins.incrementAndGet();
                } catch (DataIntegrityViolationException expected) {
                    constraintLosses.incrementAndGet();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(1, wins.get(), "동시 경쟁에서 승자는 정확히 1");
        assertEquals(1, constraintLosses.get(), "패자는 제약 위반(DataIntegrityViolationException)으로 거부");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM deploy_jobs WHERE program_id = " + pid, Long.class));
    }

    // ---------- Case 2·3 · DONE/FAILED 이후에는 새 active 작업 허용 ----------
    @Test
    void 종료된_작업이_있는_프로그램은_새_active_작업을_받을_수_있다() {
        insertJob(201L, DeployJobStatus.DONE);
        assertNotNull(insertJob(201L, DeployJobStatus.QUEUED), "DONE 뒤 새 QUEUED 허용");

        insertJob(202L, DeployJobStatus.FAILED);
        assertNotNull(insertJob(202L, DeployJobStatus.QUEUED), "FAILED 뒤 새 QUEUED 허용");
    }

    // ---------- Case 4 · active 존재 중 추가 active 는 상태 무관 거부 ----------
    @Test
    void active_작업이_있으면_QUEUED_든_RUNNING_이든_추가_INSERT_가_거부된다() {
        insertJob(301L, DeployJobStatus.QUEUED);
        try {
            insertJob(301L, DeployJobStatus.QUEUED);
            throw new AssertionError("QUEUED 중복이 허용되면 안 된다");
        } catch (DataIntegrityViolationException expected) { /* 정상 */ }
        try {
            insertJob(301L, DeployJobStatus.RUNNING);
            throw new AssertionError("QUEUED 존재 중 RUNNING 추가가 허용되면 안 된다");
        } catch (DataIntegrityViolationException expected) { /* 정상 */ }

        // 상태 전이(UPDATE)는 제약과 충돌하지 않는다: QUEUED → RUNNING → DONE → 새 작업 허용
        DeployJob j = newTx().execute(s -> {
            DeployJob x = jobs.findAll().get(0);
            x.setStatus(DeployJobStatus.RUNNING);
            return jobs.saveAndFlush(x);
        });
        assertEquals(DeployJobStatus.RUNNING, j.getStatus());
        newTx().executeWithoutResult(s -> {
            DeployJob x = jobs.findAll().get(0);
            x.setStatus(DeployJobStatus.DONE);
            jobs.saveAndFlush(x);
        });
        assertNotNull(insertJob(301L, DeployJobStatus.QUEUED));
    }

    // ---------- Case 5 · 서로 다른 프로그램은 동시 active 허용 ----------
    @Test
    void 서로_다른_프로그램은_동시에_active_작업을_가질_수_있다() throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<?> a = pool.submit(() -> {
            try { start.await(10, TimeUnit.SECONDS); insertJob(401L, DeployJobStatus.QUEUED); }
            catch (Exception e) { throw new RuntimeException(e); }
        });
        Future<?> b = pool.submit(() -> {
            try { start.await(10, TimeUnit.SECONDS); insertJob(402L, DeployJobStatus.QUEUED); }
            catch (Exception e) { throw new RuntimeException(e); }
        });
        a.get(30, TimeUnit.SECONDS);
        b.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        assertEquals(2, jobs.count());
    }

    // ---------- Case 6 · 서비스 계층의 제약 위반 처리(SlugClaims·enqueue 멱등) ----------
    @Test
    void 제약_위반이_서비스_계층에서_정상_처리된다() {
        // SlugClaims: PK 경쟁 패배를 DataIntegrityViolationException 으로 받아 소유 재확인
        assertTrue(slugClaims.claim("it-slug", 501L), "신규 slug 예약 성공");
        assertTrue(slugClaims.claim("it-slug", 501L), "같은 프로그램 재예약(재배포) 허용");
        assertFalse(slugClaims.claim("it-slug", 502L), "다른 프로그램의 선점 slug 는 거부");

        // enqueue 멱등: active 작업 존재 시 새로 만들지 않고 기존 작업을 반환(빠른 경로)
        var first = jobService.enqueue(503L, "https://gitea.example/r.git", "main", "it");
        var second = jobService.enqueue(503L, "https://gitea.example/r.git", "main", "it");
        assertEquals(first.id(), second.id(), "active 존재 시 enqueue 는 기존 작업 반환");
    }

    // ---------- SKIP LOCKED · 잠긴 행 건너뛰기(동시 트랜잭션 실측) ----------
    @Test
    void SKIP_LOCKED_는_다른_트랜잭션이_잠근_행을_건너뛴다() throws Exception {
        Long id1 = insertJob(601L, DeployJobStatus.QUEUED).getId();
        Long id2 = insertJob(602L, DeployJobStatus.QUEUED).getId();

        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        // T1: id1(가장 오래된 QUEUED)을 잠근 채 대기
        Future<Long> t1 = pool.submit(() -> newTx().execute(s -> {
            Long got = jobs.claimNextId();
            locked.countDown();
            try { release.await(20, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
            return got;
        }));
        assertTrue(locked.await(20, TimeUnit.SECONDS), "T1 이 행을 선점해야 한다");

        // T2: T1 이 잠근 행을 건너뛰고 다음 행을 가져와야 한다
        Long t2got = pool.submit(() -> newTx().execute(s -> jobs.claimNextId()))
                .get(20, TimeUnit.SECONDS);
        release.countDown();
        Long t1got = t1.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(id1, t1got, "T1 은 가장 오래된 QUEUED 를 선점");
        assertEquals(id2, t2got, "T2 는 잠긴 행을 건너뛰고(SKIP LOCKED) 다음 행을 선점");
    }

    // ---------- SKIP LOCKED · 두 워커 경쟁 소진: 중복·유실 없음 ----------
    @Test
    void 두_워커가_경쟁_소진해도_중복_처리와_유실이_없다() throws Exception {
        final int total = 12;
        for (int i = 0; i < total; i++) insertJob(700L + i, DeployJobStatus.QUEUED);

        List<Long> claimed = java.util.Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Runnable worker = () -> {
            while (true) {
                DeployJob j = jobService.claimNext();   // claim → RUNNING 전이(커밋)
                if (j == null) break;
                claimed.add(j.getId());
            }
        };
        Future<?> w1 = pool.submit(worker);
        Future<?> w2 = pool.submit(worker);
        w1.get(60, TimeUnit.SECONDS);
        w2.get(60, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(total, claimed.size(), "모든 작업이 정확히 한 번씩 선점(유실 없음)");
        assertEquals(total, claimed.stream().distinct().count(), "같은 작업 중복 선점 없음");
    }

    // ---------- 생성 컬럼 자체 검증 · active 에서만 값, 종료 상태는 NULL ----------
    @Test
    void 생성_컬럼은_active_상태에서만_program_id_를_가진다() {
        Long id = insertJob(801L, DeployJobStatus.QUEUED).getId();
        assertEquals(801L, jdbc.queryForObject(
                "SELECT active_program_id FROM deploy_jobs WHERE id = " + id, Long.class));

        newTx().executeWithoutResult(s -> {
            DeployJob x = jobs.findById(id).orElseThrow();
            x.setStatus(DeployJobStatus.FAILED);
            jobs.saveAndFlush(x);
        });
        assertNull(jdbc.queryForObject(
                "SELECT active_program_id FROM deploy_jobs WHERE id = " + id, Long.class),
                "종료 상태에서는 생성 컬럼이 NULL(제약 대상에서 제외)");
    }
}
