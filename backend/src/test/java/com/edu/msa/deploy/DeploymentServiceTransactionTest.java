package com.edu.msa.deploy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.edu.msa.common.DeploymentStatus;
import com.edu.msa.common.ProgramStatus;
import com.edu.msa.deploy.domain.Deployment;
import com.edu.msa.deploy.dto.DeployDtos.DeployRequest;
import com.edu.msa.deploy.dto.DeployDtos.DeploymentResponse;
import com.edu.msa.deploy.repository.DeploymentRepository;
import com.edu.msa.program.domain.Program;
import com.edu.msa.program.repository.ProgramRepository;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * deploy() 의 트랜잭션 경계 검증.
 * 장시간 외부 작업(git clone 등) 구간은 DB 트랜잭션 밖에서 실행되어야 하고,
 * 상태 기록(VALIDATING/…/RUNNING/FAILED)은 짧은 트랜잭션으로 즉시 커밋되어야 한다.
 * (test 프로파일: simulate 모드 · H2 · 배포 워커 비활성)
 */
@SpringBootTest
@ActiveProfiles("test")
class DeploymentServiceTransactionTest {

    @Autowired private DeploymentService service;
    @Autowired private DeploymentRepository deployments;
    @Autowired private ProgramRepository programs;

    @MockBean private SourceResolver resolver;

    private static String serviceYaml(String slug) {
        return "name: TX 테스트 서비스\n"
                + "slug: " + slug + "\n"
                + "category: doc\n"
                + "port: 8080\n"
                + "health: /healthz\n";
    }

    @Test
    void 장시간_외부작업_구간은_트랜잭션_밖에서_실행되고_상태는_짧은_트랜잭션으로_선커밋된다() {
        AtomicBoolean txActiveDuringResolve = new AtomicBoolean(true);
        AtomicReference<DeploymentStatus> committedStatusDuringResolve = new AtomicReference<>();

        when(resolver.resolve(any(), any())).thenAnswer(inv -> {
            // git clone 에 해당하는 구간 — 여기서 활성 트랜잭션이 있으면 커넥션을 점유한 것
            txActiveDuringResolve.set(TransactionSynchronizationManager.isActualTransactionActive());
            // 별도 트랜잭션에서 조회 → VALIDATING 체크포인트가 이미 커밋됐는지 확인
            deployments.findAll().stream()
                    .max(Comparator.comparing(Deployment::getId))
                    .ifPresent(d -> committedStatusDuringResolve.set(d.getStatus()));
            return new SourceMaterial(serviceYaml("tx-flow-svc"), true, "test", null);
        });

        DeploymentResponse res = service.deploy(
                new DeployRequest(null, "https://github.com/test/tx-flow", "main", "tester"));

        assertFalse(txActiveDuringResolve.get(), "외부 작업 구간에 활성 DB 트랜잭션이 없어야 한다");
        assertEquals(DeploymentStatus.VALIDATING, committedStatusDuringResolve.get(),
                "외부 작업 시작 전에 VALIDATING 상태가 커밋되어 있어야 한다");
        assertEquals(DeploymentStatus.RUNNING, res.status());
        assertEquals(DeploymentStatus.RUNNING,
                deployments.findById(res.id()).orElseThrow().getStatus(), "최종 RUNNING 상태가 커밋되어야 한다");
    }

    @Test
    void 소스수집_실패시_FAILED_상태와_로그가_커밋된다() {
        when(resolver.resolve(any(), any())).thenThrow(new DeployException("git clone 실패"));

        DeploymentResponse res = service.deploy(
                new DeployRequest(null, "https://github.com/test/tx-fail", "main", "tester"));

        assertEquals(DeploymentStatus.FAILED, res.status());
        Deployment saved = deployments.findById(res.id()).orElseThrow();
        assertEquals(DeploymentStatus.FAILED, saved.getStatus());
        assertNotNull(saved.getLogText());
    }

    @Test
    void 성공시_연결된_프로그램_공개가_배포완료와_함께_커밋된다() {
        Program p = new Program();
        p.setName("TX 연동 프로그램");
        p.setSlug("tx-linked-program");
        p.setOwner("tester");
        p.setStatus(ProgramStatus.PENDING);
        p = programs.save(p);

        when(resolver.resolve(any(), any())).thenAnswer(inv ->
                new SourceMaterial(serviceYaml("tx-linked-svc"), true, "test", null));

        DeploymentResponse res = service.deploy(
                new DeployRequest(p.getId(), "https://github.com/test/tx-linked", "main", "tester"));

        assertEquals(DeploymentStatus.RUNNING, res.status());
        assertEquals(ProgramStatus.PUBLIC,
                programs.findById(p.getId()).orElseThrow().getStatus(),
                "배포 성공 트랜잭션에서 프로그램이 PUBLIC 으로 전환되어야 한다");
    }
}
