package com.edu.msa.gitea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edu.msa.common.Role;
import com.edu.msa.gitea.repository.GiteaAccountRepository;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Gitea 계정 셀프 발급 API — Gitea 관리자 API 는 로컬 스텁 HTTP 서버로 대체해
 * 요청 형식(관리자 토큰·본문)과 응답 분기(201/422)를 검증한다.
 * 대상 식별은 JWT uid 뿐 — 본문에 다른 사용자를 지정할 방법 자체가 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GiteaAccountApiTest {

    /** 스텁 Gitea — username "taken" 이면 422(already exists), 그 외 201. */
    private static final HttpServer STUB;
    private static final List<String> AUTH_HEADERS = new CopyOnWriteArrayList<>();
    private static final List<String> BODIES = new CopyOnWriteArrayList<>();

    static {
        try {
            STUB = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            STUB.createContext("/api/v1/admin/users", ex -> {
                AUTH_HEADERS.add(ex.getRequestHeaders().getFirst("Authorization"));
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                BODIES.add(body);
                byte[] resp;
                int code;
                if (body.contains("\"taken\"")) {
                    code = 422;
                    resp = "{\"message\":\"user already exists [name: taken]\"}".getBytes(StandardCharsets.UTF_8);
                } else {
                    code = 201;
                    resp = "{}".getBytes(StandardCharsets.UTF_8);
                }
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(code, resp.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(resp); }
            });
            STUB.start();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void giteaProps(DynamicPropertyRegistry registry) {
        registry.add("edu.deploy.gitea-host", () -> "gitea.test.internal");
        registry.add("edu.deploy.gitea-clone-base",
                () -> "http://127.0.0.1:" + STUB.getAddress().getPort());
        registry.add("edu.deploy.gitea-admin-token", () -> "test-admin-token");
    }

    @AfterAll
    static void stopStub() { STUB.stop(0); }

    @Autowired private MockMvc mvc;
    @Autowired private GiteaAccountRepository repo;
    @Value("${edu.jwt.secret}") private String secret;
    @Value("${edu.jwt.issuer}") private String issuer;

    private String token(long uid, String name) {
        return Jwts.builder()
                .issuer(issuer).subject("user-" + uid)
                .claim("uid", uid).claim("name", name).claim("dept", "테스트과")
                .claim("role", Role.USER.name()).claim("typ", "access")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Test
    void 미발급_사용자는_enabled만_참인_상태를_받는다() throws Exception {
        mvc.perform(get("/api/gitea/account")
                        .header("Authorization", "Bearer " + token(8101, "김상태")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.issued").value(false))
                .andExpect(jsonPath("$.host").value("gitea.test.internal"));
    }

    @Test
    void 발급하면_Gitea_관리자_API_호출과_매핑_저장이_이뤄진다() throws Exception {
        mvc.perform(post("/api/gitea/account")
                        .header("Authorization", "Bearer " + token(8102, "이발급"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"peter\",\"password\":\"secret-pass-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.issued").value(true))
                .andExpect(jsonPath("$.username").value("peter"));

        assertEquals("peter", repo.findByAccountId(8102L).orElseThrow().getGiteaUsername());
        assertTrue(AUTH_HEADERS.contains("token test-admin-token"), "관리자 토큰으로 호출해야 한다");
        assertTrue(BODIES.stream().anyMatch(b -> b.contains("\"peter\"") && b.contains("이발급")),
                "아이디와 표시 이름(full_name)이 본문에 실려야 한다");

        // 같은 사용자의 재발급은 409 — 사용자당 1계정.
        mvc.perform(post("/api/gitea/account")
                        .header("Authorization", "Bearer " + token(8102, "이발급"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"peter2\",\"password\":\"secret-pass-1\"}"))
                .andExpect(status().isConflict());

        // 상태 조회에 발급 결과가 반영된다.
        mvc.perform(get("/api/gitea/account")
                        .header("Authorization", "Bearer " + token(8102, "이발급")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issued").value(true))
                .andExpect(jsonPath("$.username").value("peter"));
    }

    @Test
    void Gitea가_아이디_중복을_거부하면_409로_전달한다() throws Exception {
        mvc.perform(post("/api/gitea/account")
                        .header("Authorization", "Bearer " + token(8103, "박중복"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"taken\",\"password\":\"secret-pass-1\"}"))
                .andExpect(status().isConflict());
        assertTrue(repo.findByAccountId(8103L).isEmpty(), "Gitea 거부 시 매핑을 저장하면 안 된다");
    }

    @Test
    void 아이디_규칙과_비밀번호_길이를_서버가_검증한다() throws Exception {
        // 한글/특수문자 아이디 거부
        mvc.perform(post("/api/gitea/account")
                        .header("Authorization", "Bearer " + token(8104, "최검증"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"한글아이디\",\"password\":\"secret-pass-1\"}"))
                .andExpect(status().isBadRequest());
        // 8자 미만 비밀번호 거부
        mvc.perform(post("/api/gitea/account")
                        .header("Authorization", "Bearer " + token(8104, "최검증"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"choiuser\",\"password\":\"short7!\"}"))
                .andExpect(status().isBadRequest());
        // 숫자로 시작하는 아이디 거부
        mvc.perform(post("/api/gitea/account")
                        .header("Authorization", "Bearer " + token(8104, "최검증"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"1abc\",\"password\":\"secret-pass-1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 미로그인_요청은_401() throws Exception {
        mvc.perform(get("/api/gitea/account")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/gitea/account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"peter\",\"password\":\"secret-pass-1\"}"))
                .andExpect(status().isUnauthorized());
    }
}
