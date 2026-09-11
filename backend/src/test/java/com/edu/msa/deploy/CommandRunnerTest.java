package com.edu.msa.deploy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * P1-4 — CommandRunner 타임아웃 견고성(플레인 JUnit — Linux 컨테이너에서 실행).
 *
 * 과거 구조는 process.start() → getInputStream().readAllBytes() → waitFor(timeout)
 * 순서라서 자식이 stdout 을 닫지 않으면(무출력 정지·부분 출력 후 정지·손자 프로세스가
 * 파이프 보유) readAllBytes 가 EOF 를 무한 대기해 timeout 에 도달하지 못했다 —
 * 배포 워커가 hung git/kubectl 하나로 영구 정지할 수 있는 구조.
 * 불변식: timeout 은 출력 스트림 상태와 무관하게 동작하고, 초과 시 프로세스
 * 트리가 정리되며, 출력은 상한 내로만 적재된다.
 */
class CommandRunnerTest {

    private final CommandRunner runner = new CommandRunner();

    @BeforeAll
    static void linuxOnly() {
        Assumptions.assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"),
                "프로세스/sh 동작은 Linux(운영과 동일 환경)에서 검증한다");
    }

    private static List<String> sh(String script) {
        return List.of("sh", "-c", script);
    }

    // ---------- 정상 명령 계약 유지 ----------

    @Test
    void 즉시_성공_명령은_출력과_exit0을_그대로_반환한다() {
        CommandRunner.Result r = runner.run(sh("echo hello"), null, 10);
        assertTrue(r.ok());
        assertEquals("hello", r.output().trim());
    }

    @Test
    void 실패_명령은_exit코드와_stderr출력을_보존한다() {
        CommandRunner.Result r = runner.run(sh("echo boom 1>&2; exit 3"), null, 10);
        assertFalse(r.ok());
        assertEquals(3, r.exitCode());
        assertTrue(r.output().contains("boom"), "stderr 는 병합 스트림으로 보존돼야 한다");
    }

    @Test
    void stdout과_stderr를_동시에_대량_출력해도_교착이_없다() {
        CommandRunner.Result r = runner.run(
                sh("i=0; while [ $i -lt 2000 ]; do echo out-$i; echo err-$i 1>&2; i=$((i+1)); done"),
                null, 30);
        assertTrue(r.ok(), "파이프 버퍼 교착 없이 종료해야 한다");
        assertTrue(r.output().contains("out-1999") && r.output().contains("err-1999"));
    }

    // ---------- 타임아웃 강제(핵심 재현) ----------

    @Test
    void 무출력_정지_프로세스는_출력과_무관하게_timeout된다() {
        Instant t0 = Instant.now();
        CommandRunner.Result r = runner.run(sh("sleep 8"), null, 1);
        long elapsedMs = Duration.between(t0, Instant.now()).toMillis();
        assertFalse(r.ok());
        assertEquals(124, r.exitCode(), "timeout 은 기존 계약(124)을 유지한다");
        assertTrue(r.output().contains("timeout"), "결과에 timeout 표시가 있어야 한다");
        assertTrue(elapsedMs < 5000,
                "timeout(1s)이 출력 EOF 대기에 막히면 안 된다 — 실제 경과 " + elapsedMs + "ms");
    }

    @Test
    void 일부_출력_후_정지해도_확보된_출력과_함께_timeout된다() {
        Instant t0 = Instant.now();
        CommandRunner.Result r = runner.run(sh("echo start; sleep 8"), null, 1);
        long elapsedMs = Duration.between(t0, Instant.now()).toMillis();
        assertEquals(124, r.exitCode());
        assertTrue(r.output().contains("start"), "정지 전 출력은 확보돼야 한다");
        assertTrue(elapsedMs < 5000, "실제 경과 " + elapsedMs + "ms");
    }

    @Test
    void timeout시_자식_프로세스도_함께_정리된다() throws Exception {
        // 손자(sleep 30)를 만들고 그 PID 를 출력한 뒤 부모도 잠든다.
        CommandRunner.Result r = runner.run(sh("sleep 30 & echo CHILD:$!; sleep 30"), null, 1);
        assertEquals(124, r.exitCode());
        Matcher m = Pattern.compile("CHILD:(\\d+)").matcher(r.output());
        assertTrue(m.find(), "자식 PID 출력을 확보해야 한다: " + r.output());
        long childPid = Long.parseLong(m.group(1));
        // 강제 종료 전파를 잠시 기다린 뒤 자식 생존 여부 확인.
        // 컨테이너 PID1 이 고아를 즉시 회수하지 않아 좀비(Z)가 남을 수 있다 —
        // 좀비는 이미 종료된 것이므로 '실행 중(R/S/D)'만 잔존으로 본다.
        boolean running = true;
        for (int i = 0; i < 20 && running; i++) {
            running = isActuallyRunning(childPid);
            if (running) Thread.sleep(250);
        }
        assertFalse(running, "timeout 후 자식(sleep) 프로세스가 잔존하면 안 된다: pid=" + childPid);
    }

    /** /proc/<pid>/stat 의 상태가 Z(좀비)면 종료로 간주한다. 항목 부재 = 종료. */
    private static boolean isActuallyRunning(long pid) {
        try {
            String stat = java.nio.file.Files.readString(java.nio.file.Path.of("/proc/" + pid + "/stat"));
            String state = stat.substring(stat.lastIndexOf(')') + 2, stat.lastIndexOf(')') + 3);
            return !"Z".equals(state) && !"X".equals(state);
        } catch (Exception gone) {
            return false;
        }
    }

    @Test
    void 종료됐지만_손자가_파이프를_잡고_있어도_출력_수집이_무한대기하지_않는다() {
        // 부모는 즉시 종료, 손자(sleep)가 상속받은 stdout 을 계속 연다 — EOF 미발생 시나리오.
        Instant t0 = Instant.now();
        CommandRunner.Result r = runner.run(sh("echo done; sleep 30 & exit 0"), null, 5);
        long elapsedMs = Duration.between(t0, Instant.now()).toMillis();
        assertTrue(r.output().contains("done"));
        assertTrue(elapsedMs < 20000, "손자의 파이프 보유가 결과 반환을 막으면 안 된다 — 경과 " + elapsedMs + "ms");
    }

    // ---------- 출력 상한 ----------

    @Test
    void 대량_출력은_상한에서_잘리고_표시가_남으며_OOM이_없다() {
        // 기본 상한(1MiB)보다 훨씬 큰 ~8MB 출력
        CommandRunner.Result r = runner.run(
                sh("head -c 8388608 /dev/zero | tr '\\0' 'a'"), null, 60);
        assertTrue(r.ok(), "출력이 커도 프로세스 자체는 정상 종료로 처리한다");
        assertTrue(r.output().length() <= 1_200_000,
                "수집 출력은 상한 근처여야 한다: " + r.output().length());
        assertTrue(r.output().contains("[output truncated"),
                "잘림 표시가 있어야 사용자가 전체 저장으로 오해하지 않는다");
    }

    // ---------- 인터럽트 ----------

    @Test
    void 실행_스레드_인터럽트시_즉시_반환하고_플래그가_복원된다() throws Exception {
        AtomicReference<CommandRunner.Result> out = new AtomicReference<>();
        AtomicReference<Boolean> flagRestored = new AtomicReference<>(false);
        Thread t = new Thread(() -> {
            out.set(runner.run(sh("sleep 10"), null, 30));
            flagRestored.set(Thread.currentThread().isInterrupted());
        });
        t.start();
        Thread.sleep(400);
        Instant t0 = Instant.now();
        t.interrupt();
        t.join(5000);
        assertFalse(t.isAlive(), "인터럽트 후 5초 안에 반환해야 한다");
        assertTrue(Duration.between(t0, Instant.now()).toMillis() < 5000);
        assertFalse(out.get().ok());
        assertTrue(flagRestored.get(), "인터럽트 플래그는 삼키지 말고 복원해야 한다");
    }

    // ---------- 워커 가용성(연속 실행) ----------

    @Test
    void timeout_이후에도_다음_정상_명령이_처리된다() {
        assertEquals(124, runner.run(sh("sleep 8"), null, 1).exitCode());
        CommandRunner.Result next = runner.run(sh("echo recovered"), null, 10);
        assertTrue(next.ok(), "hung 명령 1건이 이후 실행을 막으면 안 된다");
        assertEquals("recovered", next.output().trim());
    }

    // ---------- 실제 git (환경 의존 — git/네트워크 없으면 스킵) ----------

    @Test
    void 실제_git_clone이_기존처럼_동작한다() {
        Optional<String> git = Optional.ofNullable(
                runner.run(List.of("git", "--version"), null, 10).ok() ? "ok" : null);
        Assumptions.assumeTrue(git.isPresent(), "git 미설치 환경 — 스킵");
        java.io.File dir = new java.io.File(System.getProperty("java.io.tmpdir"),
                "p14-clone-" + System.nanoTime());
        CommandRunner.Result r = runner.run(List.of("git", "clone", "--depth", "1",
                "https://github.com/05solar/test-code", dir.getAbsolutePath()), null, 120);
        Assumptions.assumeTrue(r.ok() || r.output().contains("unable to access"),
                "네트워크 불가 환경 — 스킵: " + r.output());
        if (r.ok()) {
            assertTrue(new java.io.File(dir, "service.yaml").exists(), "clone 결과가 유효해야 한다");
        }
    }
}
