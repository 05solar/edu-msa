package com.edu.msa.deploy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edu.msa.common.DeployJobStatus;
import com.edu.msa.common.DeploymentStatus;
import com.edu.msa.deploy.dto.DeployDtos.DeployRequest;
import com.edu.msa.deploy.dto.DeployDtos.DeploymentResponse;
import com.edu.msa.deploy.repository.DeployJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * P1-2 — 악성 service.yaml 은 빌드/적용 이전(파싱·검증)에서 차단되고,
 * 영구 오류로 종료돼 재시도 큐에 들어가지 않으며, kubectl 이 한 번도 호출되지
 * 않아야 한다(K8s 리소스·Kaniko Job 생성 없음).
 */
@SpringBootTest(properties = "edu.deploy.mode=real")
@ActiveProfiles("test")
class MaliciousSpecPipelineTest {

    @Autowired private DeploymentService service;
    @Autowired private DeployJobService jobs;
    @Autowired private DeployJobRepository jobRepo;

    @MockBean private SourceResolver resolver;
    @MockBean private CommandRunner runner;

    @Test
    void 악성_YAML은_빌드_전_차단되고_kubectl이_호출되지_않는다() {
        String malicious = "name: !!javax.script.ScriptEngineManager []\nslug: evil-svc\n";
        when(resolver.resolve(any(), any())).thenReturn(
                new SourceMaterial(malicious, true, "test", null, false));

        DeploymentResponse res = service.deploy(
                new DeployRequest(null, "https://github.com/evil/repo", "main", "t"));

        assertEquals(DeploymentStatus.FAILED, res.status());
        assertTrue(res.permanentFailure(), "파싱 오류는 영구 오류여야 한다(재시도 금지)");
        verify(runner, never()).run(any(), any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void 주입_시도_YAML은_검증에서_차단되고_영구_오류로_큐가_종료된다() {
        String injection = "name: 서비스\nslug: evil2-svc\ncategory: doc\nport: 8080\n"
                + "health: \"/h\\\", privileged: \\\"true\"\n";
        when(resolver.resolve(any(), any())).thenReturn(
                new SourceMaterial(injection, true, "test", null, false));

        // 워커 경로 그대로: enqueue → claim → deploy → terminal
        Long jobId = jobs.enqueue(8801L, "https://github.com/evil/repo2", "main", "t").id();
        jobs.claimNext();
        DeploymentResponse res = service.deploy(
                new DeployRequest(8801L, "https://github.com/evil/repo2", "main", "t"));
        assertEquals(DeploymentStatus.FAILED, res.status());
        assertTrue(res.permanentFailure());
        jobs.completeTerminal(jobId, "영구 오류");

        var job = jobRepo.findById(jobId).orElseThrow();
        assertEquals(DeployJobStatus.FAILED, job.getStatus(), "terminal FAILED — 재시도 없음");
        assertEquals(1, job.getAttempts());
        verify(runner, never()).run(any(), any(), org.mockito.ArgumentMatchers.anyLong());
    }
}
