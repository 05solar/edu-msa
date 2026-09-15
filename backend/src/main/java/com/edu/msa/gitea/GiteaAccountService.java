package com.edu.msa.gitea;

import com.edu.msa.deploy.DeployProperties;
import com.edu.msa.gitea.domain.GiteaAccount;
import com.edu.msa.gitea.dto.GiteaDtos.StatusResponse;
import com.edu.msa.gitea.repository.GiteaAccountRepository;
import com.edu.msa.security.AuthPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gitea 계정 셀프 발급 — 포털 로그인 사용자가 영문 아이디/비밀번호를 정해 본인 Gitea
 * 계정을 만든다(SSO 도입 전까지의 운영 방식 — GITEA_PLAN §7 참고).
 *
 * 생성은 Gitea 관리자 API(POST /api/v1/admin/users)로 하며, 자격은 write:admin 스코프
 * 토큰(EDU_GITEA_ADMIN_TOKEN — bootstrap 이 edu-gitea-admin-token Secret 으로 발급).
 * 배포 봇 토큰(EDU_GITEA_TOKEN, read:repository)과는 별개다. 미설정 시 기능 비활성.
 *
 * 사용자당 1계정은 gitea_accounts 의 account_id 유니크가, 아이디 선점은
 * gitea_username 유니크 + Gitea 자체 중복 검사가 보장한다.
 */
@Service
public class GiteaAccountService {

    private static final Logger log = LoggerFactory.getLogger(GiteaAccountService.class);

    private final GiteaAccountRepository repo;
    private final DeployProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    public GiteaAccountService(GiteaAccountRepository repo, DeployProperties props, ObjectMapper mapper) {
        this.repo = repo;
        this.props = props;
        this.mapper = mapper;
    }

    public boolean enabled() {
        return props.giteaHost() != null && !props.giteaHost().isBlank()
                && props.giteaAdminToken() != null && !props.giteaAdminToken().isBlank();
    }

    @Transactional(readOnly = true)
    public StatusResponse status(AuthPrincipal principal) {
        if (!enabled()) return new StatusResponse(false, false, null, null);
        return repo.findByAccountId(principal.id())
                .map(a -> new StatusResponse(true, true, a.getGiteaUsername(), props.giteaHost()))
                .orElseGet(() -> new StatusResponse(true, false, null, props.giteaHost()));
    }

    @Transactional
    public StatusResponse create(AuthPrincipal principal, String username, String password) {
        if (!enabled()) {
            throw new com.edu.msa.common.NotFoundException("Gitea 연동이 구성되지 않았습니다.");
        }
        repo.findByAccountId(principal.id()).ifPresent(a -> {
            throw new com.edu.msa.common.ConflictException(
                    "이미 Gitea 계정을 발급받았습니다: " + a.getGiteaUsername());
        });

        createOnGitea(principal, username, password);

        // 유니크 제약(account_id/gitea_username)이 동시 발급 경쟁의 최종 심판 —
        // 패자는 DataIntegrityViolationException(409) 경로로 흘러간다.
        repo.save(new GiteaAccount(principal.id(), username));
        log.info("Gitea 계정 발급: uid={} → {}", principal.id(), username);
        return new StatusResponse(true, true, username, props.giteaHost());
    }

    /** Gitea 관리자 API 로 실제 계정을 만든다. 실패 시 사용자에게 보여줄 메시지로 변환. */
    private void createOnGitea(AuthPrincipal principal, String username, String password) {
        // 이메일은 Gitea 필수 항목 — 포털은 이메일 클레임이 없으므로 내부용 주소를 만든다
        // (봇 계정 edu-deploy-bot@edu.local 과 동일한 규칙).
        Map<String, Object> body = Map.of(
                "username", username,
                "password", password,
                "email", username + "@edu.local",
                "full_name", principal.name() == null ? username : principal.name(),
                "must_change_password", false);
        HttpResponse<String> resp;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase() + "/api/v1/admin/users"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "token " + props.giteaAdminToken())
                    .POST(HttpRequest.BodyPublishers.ofString(
                            mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            log.warn("Gitea 계정 생성 호출 실패: {}", e.toString());
            throw new IllegalStateException("Gitea 서버에 연결할 수 없습니다. 잠시 후 다시 시도하세요.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("요청이 중단되었습니다. 다시 시도하세요.");
        }
        if (resp.statusCode() == 201) return;
        // 422: 아이디 중복/예약어/비밀번호 정책 위반 등 — Gitea 메시지를 요약해 전달한다.
        String detail = extractMessage(resp.body());
        if (resp.statusCode() == 422 || resp.statusCode() == 400) {
            if (detail != null && detail.toLowerCase().contains("already exists")) {
                throw new com.edu.msa.common.ConflictException("이미 사용 중인 Gitea 아이디입니다: " + username);
            }
            throw new IllegalArgumentException(
                    "Gitea 가 계정 생성을 거부했습니다" + (detail == null ? "." : ": " + detail));
        }
        // 401/403 은 관리자 토큰 구성 문제(운영자 몫) — 사용자에게는 일반 메시지.
        log.error("Gitea 계정 생성 실패: status={} body={}", resp.statusCode(), resp.body());
        throw new IllegalStateException("Gitea 계정 생성에 실패했습니다. 운영 관리자에게 문의하세요.");
    }

    /** API 실제 접근 주소 — 인클러스터/로컬은 clone-base, 미설정 시 공개 호스트(https). */
    private String apiBase() {
        String base = props.giteaCloneBase();
        if (base != null && !base.isBlank()) return base.replaceAll("/+$", "");
        return "https://" + props.giteaHost();
    }

    private String extractMessage(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            JsonNode node = mapper.readTree(body);
            if (node.hasNonNull("message")) return node.get("message").asText();
        } catch (Exception ignore) {
            // JSON 이 아니면 원문 일부만
        }
        return body.length() > 200 ? body.substring(0, 200) : body;
    }
}
