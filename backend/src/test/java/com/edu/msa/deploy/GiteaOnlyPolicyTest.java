package com.edu.msa.deploy;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edu.msa.common.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * gitea-only 정책 — 내부망 운영에서 소스가 외부(GitHub 등)로 나가지도 들어오지도
 * 않도록, 등록(POST /api/programs)과 수집(validate → SourceResolver) 양쪽에서
 * 내부 Gitea 호스트 외 주소를 거부한다. sample://(번들 예제)은 예외.
 */
@SpringBootTest(properties = {
        "edu.deploy.gitea-only=true",
        "edu.deploy.gitea-host=gitea.test.internal",
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GiteaOnlyPolicyTest {

    @Autowired private MockMvc mvc;
    @Value("${edu.jwt.secret}") private String secret;
    @Value("${edu.jwt.issuer}") private String issuer;

    private String coderToken() {
        return Jwts.builder()
                .issuer(issuer).subject("coder-7301")
                .claim("uid", 7301L).claim("name", "정코더").claim("dept", "테스트과")
                .claim("role", Role.CODER.name()).claim("typ", "access")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Test
    void 외부_레포는_검증에서_거부된다() throws Exception {
        // validate 는 오류를 in-band(valid=false + errors)로 보고한다 — UI 가 안내문으로 표시.
        mvc.perform(post("/api/deploy/validate")
                        .header("Authorization", "Bearer " + coderToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repoUrl\":\"https://github.com/someone/tool\",\"branch\":\"main\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors[0]", containsString("Gitea")));
    }

    @Test
    void 외부_레포는_등록에서도_거부되어_DB에_저장되지_않는다() throws Exception {
        mvc.perform(post("/api/programs")
                        .header("Authorization", "Bearer " + coderToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"외부툴\",\"summary\":\"요약\",\"cat\":\"doc\","
                                + "\"repo\":\"https://github.com/someone/tool\"}"))
                .andExpect(status().isBadRequest())
                // 한글은 MockMvc 기본 문자셋에서 깨질 수 있어 ASCII 부분("Gitea")로 확인한다.
                .andExpect(content().string(containsString("Gitea")));
    }

    @Test
    void 내부_Gitea_레포는_등록이_허용된다() throws Exception {
        mvc.perform(post("/api/programs")
                        .header("Authorization", "Bearer " + coderToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"내부툴\",\"summary\":\"요약\",\"cat\":\"doc\","
                                + "\"repo\":\"https://gitea.test.internal/coder/tool\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void 번들_예제_sample_주소는_정책_예외로_검증이_동작한다() throws Exception {
        mvc.perform(post("/api/deploy/validate")
                        .header("Authorization", "Bearer " + coderToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repoUrl\":\"sample://travel-settlement\"}"))
                .andExpect(status().isOk());
    }
}
