package com.edu.msa.deploy;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 배포 파이프라인 임시 파일/디렉터리 정리(베스트 에포트).
 * 정리 실패는 배포 결과에 영향을 주지 않되, 경고 로그와 메트릭
 * (edu.deploy.cleanup.failures)으로 반드시 드러나게 한다.
 */
@Component
public class TempCleaner {

    private static final Logger log = LoggerFactory.getLogger(TempCleaner.class);

    private final Counter failures;

    public TempCleaner(MeterRegistry registry) {
        this.failures = Counter.builder("edu.deploy.cleanup.failures")
                .description("배포 임시 파일/디렉터리 정리 실패 횟수")
                .register(registry);
    }

    /** 디렉터리 전체 삭제(하위 포함). 실패해도 예외를 던지지 않는다. */
    public void deleteRecursively(String dir, String what) {
        if (dir == null || dir.isBlank()) return;
        Path root = Path.of(dir);
        if (!Files.exists(root)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            // 하위 파일부터 지워야 디렉터리를 지울 수 있다(깊은 경로 우선).
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    // 개별 항목 실패는 계속 진행 — 마지막에 루트 존재 여부로 판정한다.
                }
            });
        } catch (Exception e) {
            // walk 자체 실패 — 아래 존재 검사에서 실패로 집계된다.
        }
        if (Files.exists(root)) {
            failures.increment();
            log.warn("임시 디렉터리 정리 실패({}) · {}", what, root);
        }
    }

    /** 단일 파일 삭제. 실패해도 예외를 던지지 않는다. */
    public void deleteFile(Path file, String what) {
        if (file == null) return;
        try {
            Files.deleteIfExists(file);
        } catch (Exception e) {
            failures.increment();
            log.warn("임시 파일 정리 실패({}) · {} · {}", what, file, e.getMessage());
        }
    }
}
