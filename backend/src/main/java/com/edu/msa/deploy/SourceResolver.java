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

    public SourceResolver(CommandRunner runner) {
        this.runner = runner;
    }

    public SourceMaterial resolve(String repoUrl, String branch) {
        if (repoUrl == null || repoUrl.isBlank()) {
            throw new DeployException("레포 주소가 비어 있습니다.");
        }
        if (repoUrl.startsWith("sample://")) {
            return fromClasspath(repoUrl.substring("sample://".length()));
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

    private SourceMaterial fromGit(String repoUrl, String branch) {
        try {
            Path tmp = Files.createTempDirectory("edu-src-");
            CommandRunner.Result r = runner.run(
                    List.of("git", "clone", "--depth", "1", "-b", branch, repoUrl, tmp.toString()),
                    null, 120);
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

    private String firstLine(String s) {
        if (s == null) return "";
        int nl = s.indexOf('\n');
        return nl >= 0 ? s.substring(0, nl) : s;
    }
}
