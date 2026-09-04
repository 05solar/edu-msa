package com.edu.msa.deploy.webhook;

import com.edu.msa.common.ProgramStatus;
import com.edu.msa.deploy.DeployJobService;
import com.edu.msa.deploy.DeployProperties;
import com.edu.msa.program.domain.Program;
import com.edu.msa.program.repository.ProgramRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gitea push webhook — 저장(push) 시 등록된 프로그램을 자동 재배포한다. (Gitea 4단계)
 *
 * 인증: 이 경로는 JWT 대신 Gitea 가 서명한 HMAC-SHA256(X-Gitea-Signature)으로 검증한다.
 * 시크릿(EDU_GITEA_WEBHOOK_SECRET) 미설정 시 엔드포인트 자체를 닫는다(404).
 * 대상: main 브랜치 push + 공개(PUBLIC) 상태 프로그램만. 그 외는 조용히 무시(200).
 */
@RestController
public class GiteaWebhookController {

    private final DeployProperties props;
    private final DeployJobService jobs;
    private final ProgramRepository programs;
    private final ObjectMapper mapper;

    public GiteaWebhookController(DeployProperties props, DeployJobService jobs,
                                  ProgramRepository programs, ObjectMapper mapper) {
        this.props = props;
        this.jobs = jobs;
        this.programs = programs;
        this.mapper = mapper;
    }

    @PostMapping("/api/webhooks/gitea")
    public ResponseEntity<?> onPush(@RequestHeader(value = "X-Gitea-Signature", required = false) String signature,
                                    @RequestHeader(value = "X-Gitea-Event", required = false) String event,
                                    @RequestBody byte[] body) {
        String secret = props.giteaWebhookSecret();
        if (secret == null || secret.isBlank()) {
            return ResponseEntity.notFound().build();   // 기능 미구성 — 존재 자체를 숨긴다
        }
        if (!signatureValid(secret, body, signature)) {
            return ResponseEntity.status(401).body(Map.of("error", "서명이 올바르지 않습니다."));
        }
        if (!"push".equalsIgnoreCase(event)) {
            return ResponseEntity.ok(Map.of("ignored", "event:" + event));
        }

        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "본문 파싱 실패"));
        }
        String ref = root.path("ref").asText("");
        if (!"refs/heads/main".equals(ref)) {
            return ResponseEntity.ok(Map.of("ignored", "ref:" + ref));   // main 만 자동 재배포
        }
        String cloneUrl = root.path("repository").path("clone_url").asText("");
        String htmlUrl = root.path("repository").path("html_url").asText("");

        Program target = programs.findByStatus(ProgramStatus.PUBLIC).stream()
                .filter(p -> matches(p.getRepoUrl(), cloneUrl) || matches(p.getRepoUrl(), htmlUrl))
                .findFirst().orElse(null);
        if (target == null) {
            return ResponseEntity.ok(Map.of("ignored", "no-matching-program"));
        }
        // 레포 주소는 서버 저장값만 사용한다(웹훅 본문 주소로 배포하지 않음 — 주입 차단).
        var job = jobs.enqueue(target.getId(), target.getRepoUrl(), target.getBranch(), "gitea-webhook");
        return ResponseEntity.accepted().body(Map.of("programId", target.getId(), "jobId", job.id()));
    }

    /** Gitea X-Gitea-Signature = HMAC-SHA256(hex, 원문 body). 상수 시간 비교로 검증한다. */
    private boolean signatureValid(String secret, byte[] body, String signature) {
        if (signature == null || signature.isBlank()) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = HexFormat.of().formatHex(mac.doFinal(body)).getBytes(StandardCharsets.US_ASCII);
            byte[] given = signature.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(expected, given);
        } catch (Exception e) {
            return false;
        }
    }

    /** 등록 주소와 webhook 주소를 정규화(소문자·.git·말미 / 제거) 후 비교한다. */
    private boolean matches(String registered, String incoming) {
        if (registered == null || incoming == null || incoming.isBlank()) return false;
        return normalize(registered).equals(normalize(incoming));
    }

    private String normalize(String url) {
        String s = url.trim().toLowerCase();
        if (s.endsWith(".git")) s = s.substring(0, s.length() - 4);
        return s.replaceAll("/+$", "");
    }
}
