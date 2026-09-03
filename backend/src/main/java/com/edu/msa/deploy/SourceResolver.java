package com.edu.msa.deploy;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 레포에서 service.yaml / Dockerfile 존재를 수집한다.
 * - sample://<name> : 번들된 예제(classpath deploy-samples/<name>) 사용 (오프라인 테스트).
 * - http(s)/git URL : git clone (real 재료 수집; git 필요).
 */
@Component
public class SourceResolver {

    private final CommandRunner runner;
    private final DeployProperties props;

    public SourceResolver(CommandRunner runner, DeployProperties props) {
        this.runner = runner;
        this.props = props;
    }

    public SourceMaterial resolve(String repoUrl, String branch) {
        if (repoUrl == null || repoUrl.isBlank()) {
            throw new DeployException("레포 주소가 비어 있습니다.");
        }
        if (repoUrl.startsWith("sample://")) {
            return fromClasspath(repoUrl.substring("sample://".length()));
        }
        if (repoUrl.startsWith("local://")) {
            return fromLocal(repoUrl.substring("local://".length()));
        }
        return fromGit(repoUrl, branch != null && !branch.isBlank() ? branch : "main");
    }

    private SourceMaterial fromClasspath(String name) {
        String base = "deploy-samples/" + name + "/";
        try (InputStream in = new ClassPathResource(base + "service.yaml").getInputStream()) {
            String yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return new SourceMaterial(yaml, true, "classpath:" + base, null);
        } catch (Exception e) {
            throw new DeployException("예제 레포를 찾을 수 없습니다: " + name);
        }
    }

    private SourceMaterial fromLocal(String path) {
        try {
            java.nio.file.Path dir = java.nio.file.Path.of(path);
            java.io.File yamlFile = dir.resolve("service.yaml").toFile();
            if (!yamlFile.exists()) {
                throw new DeployException("경로에 service.yaml 이 없습니다: " + path);
            }
            String yaml = Files.readString(yamlFile.toPath(), StandardCharsets.UTF_8);
            boolean hasDockerfile = dir.resolve("Dockerfile").toFile().exists();
            return new SourceMaterial(yaml, hasDockerfile, "local:" + path, dir.toString());
        } catch (DeployException e) {
            throw e;
        } catch (Exception e) {
            throw new DeployException("로컬 소스 읽기 오류: " + e.getMessage());
        }
    }

    private SourceMaterial fromGit(String repoUrl, String branch) {
        try {
            // 내부 Gitea 는 공개 주소로 등록되지만 실제 수집은 내부 접근 주소(clone-base)로 한다.
            String cloneUrl = props.rewriteGiteaUrl(repoUrl);
            Path tmp = Files.createTempDirectory("edu-src-");
            CommandRunner.Result r = runner.run(
                    List.of("git", "clone", "--depth", "1", "-b", branch, cloneUrl, tmp.toString()),
                    null, 120, gitEnv(repoUrl, cloneUrl));
            if (!r.ok()) {
                throw new DeployException("레포를 가져오지 못했습니다(git clone 실패). " + firstLine(r.output()));
            }
            File yamlFile = tmp.resolve("service.yaml").toFile();
            if (!yamlFile.exists()) {
                throw new DeployException("레포 루트에 service.yaml 이 없습니다.");
            }
            String yaml = Files.readString(yamlFile.toPath(), StandardCharsets.UTF_8);
            boolean hasDockerfile = tmp.resolve("Dockerfile").toFile().exists();
            return new SourceMaterial(yaml, hasDockerfile, repoUrl + "#" + branch, tmp.toString());
        } catch (DeployException e) {
            throw e;
        } catch (Exception e) {
            throw new DeployException("레포 수집 중 오류: " + e.getMessage());
        }
    }

    /**
     * 내부 Gitea 비공개 레포 clone 자격 증명(3단계).
     * 토큰을 URL·인자에 넣지 않고 GIT_CONFIG 환경변수(extraHeader)로 주입해
     * 프로세스 목록·배포 로그·오류 메시지에 노출되지 않게 한다. 그 외 레포는 빈 환경(기존 동작).
     */
    private java.util.Map<String, String> gitEnv(String repoUrl, String cloneUrl) {
        if (!props.isGiteaRepo(repoUrl) || props.giteaToken() == null || props.giteaToken().isBlank()) {
            return java.util.Map.of();
        }
        String basic = java.util.Base64.getEncoder().encodeToString(
                (props.giteaUser() + ":" + props.giteaToken()).getBytes(StandardCharsets.UTF_8));
        // extraHeader 매칭 키는 실제 clone 에 쓰는 주소(origin)를 기준으로 한다.
        int pathStart = cloneUrl.indexOf('/', cloneUrl.indexOf("://") + 3);
        String origin = (pathStart > 0 ? cloneUrl.substring(0, pathStart) : cloneUrl) + "/";
        return java.util.Map.of(
                "GIT_CONFIG_COUNT", "1",
                "GIT_CONFIG_KEY_0", "http." + origin + ".extraHeader",
                "GIT_CONFIG_VALUE_0", "Authorization: Basic " + basic,
                "GIT_TERMINAL_PROMPT", "0");
    }

    private String firstLine(String s) {
        if (s == null) return "";
        int nl = s.indexOf('\n');
        return nl >= 0 ? s.substring(0, nl) : s;
    }
}
