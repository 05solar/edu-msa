package com.edu.msa.deploy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 외부 명령(git/kubectl/docker) 실행. real 모드에서만 사용된다.
 *
 * [P1-4] 타임아웃 견고성:
 *  · 과거에는 readAllBytes()(EOF 대기)가 waitFor(timeout) 앞에 있어 자식이 stdout 을
 *    닫지 않으면(무출력 정지·손자 프로세스의 파이프 보유) 타임아웃이 무력화됐다 —
 *    hung git/kubectl 하나가 배포 워커를 영구 정지시킬 수 있는 구조.
 *  · 지금은 출력 수집을 공유 데몬 리더 스레드로 분리하고 본 스레드는 곧장
 *    waitFor(timeout) 한다. 타임아웃 시 destroy → grace → destroyForcibly 를
 *    프로세스 트리(descendants 포함)에 적용하고 스트림을 닫아 리더도 해제한다.
 *  · 출력은 상한(기본 1MiB)까지만 적재하고 초과분은 버리며 "[output truncated]" 를
 *    남긴다 — 폭주 출력으로 워커 heap/DB logText 가 부풀지 않는다.
 *  · stdin 은 즉시 닫는다 — 자격 증명 프롬프트 등 입력 대기 행(hang) 방지.
 *  · 실행은 argv 배열 그대로다(셸 문자열 실행 금지 — 주입 면 없음, 기존 유지).
 */
@Component
public class CommandRunner {

    public record Result(int exitCode, String output) {
        public boolean ok() { return exitCode == 0; }
    }

    /** 출력 리더 전용 공유 풀 — 데몬 스레드라 종료를 막지 않고, 유휴 시 회수된다. */
    private static final ExecutorService OUTPUT_READERS = Executors.newCachedThreadPool(
            new ThreadFactory() {
                private final AtomicInteger seq = new AtomicInteger();
                @Override public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "cmd-output-reader-" + seq.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            });

    private final long maxOutputBytes;
    private final long killGraceMs;

    public CommandRunner(
            @Value("${edu.deploy.command.max-output-bytes:1048576}") long maxOutputBytes,
            @Value("${edu.deploy.command.kill-grace-ms:3000}") long killGraceMs) {
        this.maxOutputBytes = maxOutputBytes;
        this.killGraceMs = killGraceMs;
    }

    /** 테스트/직접 생성용 — 운영 기본값과 동일. */
    public CommandRunner() {
        this(1_048_576, 3_000);
    }

    public Result run(List<String> command, File workDir, long timeoutSeconds) {
        return run(command, workDir, timeoutSeconds, java.util.Map.of());
    }

    /** 자격 증명 등 민감 값은 인자(argv)가 아니라 환경변수로 전달한다(프로세스 목록·로그 비노출). */
    public Result run(List<String> command, File workDir, long timeoutSeconds, java.util.Map<String, String> env) {
        Process proc = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
            if (workDir != null) pb.directory(workDir);
            if (env != null && !env.isEmpty()) pb.environment().putAll(env);
            proc = pb.start();
            closeQuietly(proc.getOutputStream());   // stdin 닫기 — 입력 프롬프트 대기 방지
            BoundedCollector collector = new BoundedCollector(maxOutputBytes);
            Future<?> reader = OUTPUT_READERS.submit(collector.readerFor(proc.getInputStream()));

            boolean finished = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                killTree(proc);
                awaitQuietly(reader, 2000);   // 트리 종료로 파이프가 닫히면 리더도 EOF 로 끝난다
                return new Result(124, collector.snapshot()
                        + "\n[timeout: " + timeoutSeconds + "초 초과 — 프로세스 강제 종료]");
            }
            // 프로세스는 끝났지만 손자가 stdout 을 상속해 EOF 가 안 올 수 있다 — 결과 반환을
            // 막지 않도록 짧게만 기다리고, 그 시점까지의 스냅샷을 반환한다(리더는 데몬).
            boolean eof = awaitQuietly(reader, 5000);
            String out = collector.snapshot();
            if (!eof) out += "\n[output stream held open by descendant — partial capture]";
            return new Result(proc.exitValue(), out);
        } catch (InterruptedException e) {
            killTree(proc);
            Thread.currentThread().interrupt();   // 인터럽트를 삼키지 않는다
            return new Result(130, "[interrupted]");
        } catch (Exception e) {
            killTree(proc);
            return new Result(127, "명령 실행 실패: " + e.getMessage());
        }
    }

    /**
     * 상한 있는 출력 수집기 — 리더 스레드가 EOF 까지 소비하되(파이프 버퍼 정체 방지)
     * 상한 초과분은 버린다. 리더가 파이프에 블로킹된 채 남아도(EOF 미발생) 메인
     * 스레드는 snapshot() 으로 그때까지의 출력을 안전하게 회수한다.
     */
    private static final class BoundedCollector {
        private final long max;
        private final java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        private long total = 0;

        BoundedCollector(long max) { this.max = max; }

        Runnable readerFor(InputStream in) {
            return () -> {
                byte[] chunk = new byte[8192];
                try (in) {
                    int n;
                    while ((n = in.read(chunk)) != -1) append(chunk, n);
                } catch (IOException closed) {
                    // 정리 경로에서 스트림이 닫힘 — 수집분은 snapshot 으로 회수된다
                }
            };
        }

        private synchronized void append(byte[] chunk, int n) {
            if (total < max) {
                buf.write(chunk, 0, (int) Math.min(n, max - total));
            }
            total += n;
        }

        synchronized String snapshot() {
            String out = buf.toString(StandardCharsets.UTF_8);
            return total > max
                    ? out + "\n[output truncated: " + max + " bytes 초과분 미저장 · 총 " + total + " bytes]"
                    : out;
        }
    }

    /** 리더 종료를 제한 시간만 기다린다. true = EOF 도달(전체 수집 완료). */
    private static boolean awaitQuietly(Future<?> reader, long ms) {
        try {
            reader.get(ms, TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 프로세스 트리 종료 — descendants 를 먼저 스냅샷한 뒤 부모→자식 순으로
     * destroy 하고, grace 안에 안 끝나면 destroyForcibly 한다.
     * 이 프로세스의 트리에만 한정된다(무관한 프로세스에 영향 없음).
     */
    private void killTree(Process proc) {
        if (proc == null) return;
        try {
            List<ProcessHandle> kids = proc.toHandle().descendants().toList();
            proc.destroy();
            kids.forEach(ProcessHandle::destroy);
            if (!proc.waitFor(killGraceMs, TimeUnit.MILLISECONDS)) {
                proc.destroyForcibly();
            }
            kids.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
            proc.waitFor(killGraceMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            proc.destroyForcibly();
            Thread.currentThread().interrupt();
        } catch (Exception ignore) {
            proc.destroyForcibly();
        }
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c == null) return;
        try { c.close(); } catch (IOException ignore) { /* 정리 경로 */ }
    }
}
