package com.edu.msa.deploy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.edu.msa.deploy.domain.Deployment;
import com.edu.msa.deploy.repository.DeploymentRepository;
import com.edu.msa.deploy.repository.SlugClaimRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * P0-2 — cleanup(removeFor)이 다른 배포의 Kubernetes 리소스를 삭제하지 않는지 검증.
 *
 * 시나리오: 프로그램 A 의 정리 시점에, 같은 slug 이름의 리소스가 이미 다른 프로그램 B
 * 소유(edu.msa/program-id 라벨)로 재생성돼 있으면 name 기반 삭제를 건너뛰어야 한다.
 * 라벨이 없는 리소스(구버전 배포 잔재)는 기존대로 정리한다.
 */
@SpringBootTest(properties = "edu.deploy.mode=real")
@ActiveProfiles("test")
class DeploymentOwnershipCleanupTest {

    @Autowired private DeploymentService service;
    @Autowired private DeploymentRepository deployments;
    @Autowired private SlugClaimRepository claims;
    @Autowired private SlugClaims slugClaims;

    @MockBean private CommandRunner runner;

    private Deployment savedDeployment(Long programId, String slug) {
        Deployment d = new Deployment(programId, "https://github.com/t/o", "main");
        d.setSlug(slug);
        return deployments.save(d);
    }

    @Test
    void 다른_프로그램_소유_리소스는_삭제하지_않는다() {
        long myProgram = 7001L;
        savedDeployment(myProgram, "owned-by-other");
        List<List<String>> calls = new ArrayList<>();
        when(runner.run(anyList(), any(), anyLong())).thenAnswer(inv -> {
            List<String> cmd = inv.getArgument(0);
            calls.add(cmd);
            if (cmd.contains("get")) {
                // 현재 클러스터의 slug 리소스는 다른 프로그램(7999) 소유라고 응답
                return new CommandRunner.Result(0, "7999");
            }
            return new CommandRunner.Result(0, "");
        });

        service.removeFor(myProgram);

        assertTrue(calls.stream().anyMatch(c -> c.contains("get")),
                "삭제 전 소유자(label) 조회를 해야 한다");
        assertFalse(calls.stream().anyMatch(c -> c.contains("delete")),
                "소유자가 다르면 어떤 delete 도 실행하면 안 된다: " + calls);
    }

    @Test
    void 본인_소유_리소스는_삭제하고_slug_예약도_반납한다() {
        long myProgram = 7002L;
        String slug = "owned-by-me";
        slugClaims.claim(slug, myProgram);
        savedDeployment(myProgram, slug);
        List<List<String>> calls = new ArrayList<>();
        when(runner.run(anyList(), any(), anyLong())).thenAnswer(inv -> {
            List<String> cmd = inv.getArgument(0);
            calls.add(cmd);
            if (cmd.contains("get")) {
                return new CommandRunner.Result(0, String.valueOf(myProgram));
            }
            return new CommandRunner.Result(0, "");
        });

        service.removeFor(myProgram);

        assertTrue(calls.stream().anyMatch(c -> c.contains("delete")),
                "본인 소유 리소스는 삭제해야 한다");
        assertTrue(claims.findById(slug).isEmpty(),
                "프로그램 삭제 시 slug 예약도 반납되어야 한다(재사용 가능)");
    }

    @Test
    void 라벨_없는_구버전_리소스는_기존대로_정리한다() {
        long myProgram = 7003L;
        savedDeployment(myProgram, "legacy-no-label");
        List<List<String>> calls = new ArrayList<>();
        when(runner.run(anyList(), any(), anyLong())).thenAnswer(inv -> {
            List<String> cmd = inv.getArgument(0);
            calls.add(cmd);
            if (cmd.contains("get")) {
                // 라벨 미존재 — go-template 은 빈 값/no value 를 낸다
                return new CommandRunner.Result(0, "<no value>");
            }
            return new CommandRunner.Result(0, "");
        });

        service.removeFor(myProgram);

        assertTrue(calls.stream().anyMatch(c -> c.contains("delete")),
                "라벨 없는 구버전 리소스는 잔존하지 않도록 정리해야 한다");
    }
}
