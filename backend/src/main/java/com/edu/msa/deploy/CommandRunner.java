package com.edu.msa.deploy;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** 외부 명령(git/docker/kubectl) 실행. real 모드에서만 사용된다. */
@Component
public class CommandRunner {

    public record Result(int exitCode, String output) {
        public boolean ok() { return exitCode == 0; }
    }

    public Result run(List<String> command, File workDir, long timeoutSeconds) {
        return run(command, workDir, timeoutSeconds, java.util.Map.of());
    }

    /** 자격 증명 등 민감 값은 인자(argv)가 아니라 환경변수로 전달한다(프로세스 목록·로그 비노출). */
    public Result run(List<String> command, File workDir, long timeoutSeconds, java.util.Map<String, String> env) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
            if (workDir != null) pb.directory(workDir);
            if (env != null && !env.isEmpty()) pb.environment().putAll(env);
            Process proc = pb.start();
            String out = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                return new Result(124, out + "\n[timeout]");
            }
            return new Result(proc.exitValue(), out);
        } catch (Exception e) {
            return new Result(127, "명령 실행 실패: " + e.getMessage());
        }
    }
}
