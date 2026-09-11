package com.edu.msa.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edu.msa.program.domain.Program;
import com.edu.msa.program.repository.ProgramRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * P1-5 — 프로그램 소유권은 불변 UID(owner_id = JWT uid)로만 판정한다.
 * 재현했던 결함: 표시 이름이 같은 다른 계정(동명이인 CODER)이 남의 프로그램을
 * 삭제할 수 있었다(staging 실측 204). 이 테스트가 그 경로를 영구 차단한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OwnershipIdentityTest {

    @Autowired private MockMvc mvc;
    @Autowired private ProgramRepository programs;
    @Autowired private ObjectMapper mapper;
    @Value("${edu.jwt.secret}") private String secret;
    @Value("${edu.jwt.issuer}") private String issuer;

    private static final String SAME_NAME = "박서준-p15";
    private static final long UID_A = 9201L;
    private static final long UID_B = 9202L;

    private String token(long uid, String name, String role) {
        return Jwts.builder()
                .issuer(issuer).subject("user-" + uid)
                .claim("uid", uid).claim("name", name).claim("dept", "테스트과")
                .claim("role", role).claim("typ", "access")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private long createAs(long uid, String name, Map<String, Object> extra) throws Exception {
        var body = new java.util.HashMap<String, Object>(Map.of(
                "name", "identity-" + uid + "-" + System.nanoTime(),
                "summary", "s", "cat", "etc", "repo", "https://github.com/t/r"));
        body.putAll(extra);
        String res = mvc.perform(post("/api/programs")
                        .header("Authorization", "Bearer " + token(uid, name, "CODER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(res).get("id").asLong();
    }

    @Test
    void 소유자는_JWT_uid로_저장되고_클라이언트_owner값은_무시된다() throws Exception {
        long id = createAs(UID_A, SAME_NAME, Map.of("owner", "위조된주인", "ownerId", 424242));
        Program p = programs.findById(id).orElseThrow();
        assertEquals(UID_A, p.getOwnerId(), "owner_id 는 항상 인증 uid 여야 한다(위조 무시)");
        assertEquals(SAME_NAME, p.getOwner(), "표시 이름도 principal 스냅샷이어야 한다");
        assertTrue(p.isOwnerTrusted(), "CODER 생성 → 내부 신뢰 tier 스냅샷");
    }

    @Test
    void 동명이인은_같은_이름이어도_삭제할_수_없다() throws Exception {
        long id = createAs(UID_A, SAME_NAME, Map.of());
        // B: 표시 이름 완전히 동일, uid 만 다름, 정식 CODER — 과거엔 삭제가 성공했다(재현됨)
        mvc.perform(delete("/api/programs/" + id)
                        .header("Authorization", "Bearer " + token(UID_B, SAME_NAME, "CODER")))
                .andExpect(status().isForbidden());
        assertTrue(programs.findById(id).isPresent(), "동명이인의 삭제 시도로 사라지면 안 된다");
        // 본인(uid A)은 정상 삭제
        mvc.perform(delete("/api/programs/" + id)
                        .header("Authorization", "Bearer " + token(UID_A, SAME_NAME, "CODER")))
                .andExpect(status().isNoContent());
    }

    @Test
    void 동명이인은_재배포도_할_수_없다() throws Exception {
        long id = createAs(UID_A, SAME_NAME, Map.of());
        mvc.perform(post("/api/programs/" + id + "/redeploy")
                        .header("Authorization", "Bearer " + token(UID_B, SAME_NAME, "CODER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 이름을_바꿔도_소유권은_유지된다() throws Exception {
        long id = createAs(UID_A, SAME_NAME, Map.of());
        // 같은 uid, 개명된 표시 이름의 새 토큰 — 소유권은 uid 를 따라간다
        mvc.perform(delete("/api/programs/" + id)
                        .header("Authorization", "Bearer " + token(UID_A, "개명한박서준", "CODER")))
                .andExpect(status().isNoContent());
    }

    @Test
    void legacy_소유자불명_프로그램은_이름이_같아도_소유자_액션이_거부되고_ADMIN은_가능하다() throws Exception {
        Program legacy = new Program();
        legacy.setName("legacy-" + System.nanoTime());
        legacy.setOwner(SAME_NAME);            // 표시 이름만 있고 owner_id 없음(마이그레이션 전 데이터)
        legacy.setCat("etc");
        Program saved = programs.save(legacy);
        mvc.perform(delete("/api/programs/" + saved.getId())
                        .header("Authorization", "Bearer " + token(UID_A, SAME_NAME, "CODER")))
                .andExpect(status().isForbidden());   // 이름 일치만으로는 소유자가 될 수 없다(fail-closed)
        mvc.perform(delete("/api/programs/" + saved.getId())
                        .header("Authorization", "Bearer " + token(7777L, "관리자", "ADMIN")))
                .andExpect(status().isNoContent());   // remediation 경로: ADMIN 정리
    }

    @Test
    void 응답에는_ownerId와_표시이름이_함께_내려간다() throws Exception {
        long id = createAs(UID_A, SAME_NAME, Map.of());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/programs/" + id)
                        .header("Authorization", "Bearer " + token(UID_A, SAME_NAME, "CODER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value((int) UID_A))
                .andExpect(jsonPath("$.owner").value(SAME_NAME));
        mvc.perform(delete("/api/programs/" + id)
                .header("Authorization", "Bearer " + token(UID_A, SAME_NAME, "CODER")));
    }
}
