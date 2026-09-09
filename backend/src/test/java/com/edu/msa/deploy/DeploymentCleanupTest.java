package com.edu.msa.deploy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.edu.msa.common.DeploymentStatus;
import com.edu.msa.deploy.dto.DeployDtos.DeployRequest;
import com.edu.msa.deploy.dto.DeployDtos.DeploymentResponse;
import com.edu.msa.deploy.dto.DeployDtos.ValidateRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * 배포 파이프라인 임시 clone 디렉터리 정리 검증.
 * 성공·실패 모두 ephemeral workDir 은 deploy()/validate() 의 finally 에서 삭제되고,
 * local:// 예제 경로(비-ephemeral)는 절대 삭제되지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
class DeploymentCleanupTest {

    @Autowired private DeploymentService service;

    @MockBean private SourceResolver resolver;

    private static String serviceYaml(String slug) {
        return "name: 정리 테스트\nslug: " + slug + "\ncategory: doc\nport: 8080\nhealth: /healthz\n";
    }

    private Path tempWorkDir() throws Exception {
        Path dir = Files.createTempDirectory("edu-src-test-");
        Files.writeString(dir.resolve("service.yaml"), "dummy");
        return dir;
    }

    @Test
    void 배포_성공시_임시_clone_디렉터리가_삭제된다() throws Exception {
        Path dir = tempWorkDir();
        when(resolver.resolve(any(), any())).thenReturn(
                new SourceMaterial(serviceYaml("cleanup-ok-svc"), true, "test", dir.toString(), true));

        DeploymentResponse res = service.deploy(
                new DeployRequest(null, "https://github.com/test/cleanup-ok", "main", "tester"));

        assertEquals(DeploymentStatus.RUNNING, res.status());
        assertFalse(Files.exists(dir), "성공 후 임시 clone 디렉터리는 삭제되어야 한다");
    }

    @Test
    void 배포_실패시에도_임시_clone_디렉터리가_삭제된다() throws Exception {
        Path dir = tempWorkDir();
        // slug 형식 위반 → 규격 검증 실패(FAILED) 경로
        when(resolver.resolve(any(), any())).thenReturn(
                new SourceMaterial(serviceYaml("Bad_Slug!"), true, "test", dir.toString(), true));

        DeploymentResponse res = service.deploy(
                new DeployRequest(null, "https://github.com/test/cleanup-fail", "main", "tester"));

        assertEquals(DeploymentStatus.FAILED, res.status());
        assertFalse(Files.exists(dir), "실패 후에도 임시 clone 디렉터리는 삭제되어야 한다");
    }

    @Test
    void 로컬_예제_경로는_ephemeral이_아니므로_삭제되지_않는다() throws Exception {
        Path dir = tempWorkDir();
        try {
            when(resolver.resolve(any(), any())).thenReturn(
                    new SourceMaterial(serviceYaml("cleanup-local-svc"), true, "local:test", dir.toString(), false));

            DeploymentResponse res = service.deploy(
                    new DeployRequest(null, "local://examples/cleanup-local", "main", "tester"));

            assertEquals(DeploymentStatus.RUNNING, res.status());
            assertTrue(Files.exists(dir), "local:// 경로(비-ephemeral)는 삭제되면 안 된다");
        } finally {
            Files.deleteIfExists(dir.resolve("service.yaml"));
            Files.deleteIfExists(dir);
        }
    }

    @Test
    void validate도_임시_clone_디렉터리를_정리한다() throws Exception {
        Path dir = tempWorkDir();
        when(resolver.resolve(any(), any())).thenReturn(
                new SourceMaterial(serviceYaml("cleanup-validate-svc"), true, "test", dir.toString(), true));

        service.validate(new ValidateRequest("https://github.com/test/cleanup-validate", "main", null));

        assertFalse(Files.exists(dir), "validate 후 임시 clone 디렉터리는 삭제되어야 한다");
    }
}
