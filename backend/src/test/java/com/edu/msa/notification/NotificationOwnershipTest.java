package com.edu.msa.notification;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edu.msa.common.NotiKind;
import com.edu.msa.notification.domain.Notification;
import com.edu.msa.notification.repository.NotificationRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * P1-1 — 알림 IDOR 차단 검증. 대상 사용자는 항상 JWT principal 로 결정되고,
 * 클라이언트가 보내는 `to` 파라미터·타인의 알림 id 로는 조회/변경이 불가능해야 한다.
 * 실제 JwtAuthenticationFilter + SecurityConfig 체인을 그대로 태운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationOwnershipTest {

    @Autowired private MockMvc mvc;
    @Autowired private NotificationService service;
    @Autowired private NotificationRepository repo;
    @Value("${edu.jwt.secret}") private String secret;
    @Value("${edu.jwt.issuer}") private String issuer;

    private static final String ALICE = "앨리스-p11";
    private static final String BOB = "밥-p11";
    private Long aliceNoti;
    private Long bobNoti;

    private String token(String name) {
        return Jwts.builder()
                .issuer(issuer).subject(name)
                .claim("uid", (long) name.hashCode())
                .claim("name", name).claim("dept", "테스트과")
                .claim("role", "USER").claim("typ", "access")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @BeforeEach
    void seed() {
        repo.deleteAll();
        service.push(ALICE, NotiKind.APPROVE, "앨리스 알림", "sub", null);
        service.push(BOB, NotiKind.APPROVE, "밥 알림", "sub", null);
        aliceNoti = repo.findByToUserOrderByIdDesc(ALICE).get(0).getId();
        bobNoti = repo.findByToUserOrderByIdDesc(BOB).get(0).getId();
    }

    @Test
    void A_목록은_to_파라미터와_무관하게_principal_알림만_반환한다() throws Exception {
        mvc.perform(get("/api/notifications").param("to", BOB)
                        .header("Authorization", "Bearer " + token(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].to").value(ALICE));
    }

    @Test
    void B_unread_count도_principal_기준이다() throws Exception {
        service.push(ALICE, NotiKind.COMMENT, "앨리스 알림 2", "sub", null);
        mvc.perform(get("/api/notifications/unread-count").param("to", BOB)
                        .header("Authorization", "Bearer " + token(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    void C_타인_알림_read는_404이고_상태가_변하지_않는다() throws Exception {
        mvc.perform(post("/api/notifications/" + bobNoti + "/read")
                        .header("Authorization", "Bearer " + token(ALICE)))
                .andExpect(status().isNotFound());
        assertFalse(repo.findById(bobNoti).orElseThrow().isRead(),
                "타인의 read 시도로 밥의 알림 상태가 변하면 안 된다");
        // 존재하지 않는 id 도 같은 404 — 존재 여부 비노출
        mvc.perform(post("/api/notifications/999999/read")
                        .header("Authorization", "Bearer " + token(ALICE)))
                .andExpect(status().isNotFound());
    }

    @Test
    void D_자기_알림_read는_성공한다() throws Exception {
        mvc.perform(post("/api/notifications/" + aliceNoti + "/read")
                        .header("Authorization", "Bearer " + token(ALICE)))
                .andExpect(status().isOk());
        assertTrue(repo.findById(aliceNoti).orElseThrow().isRead());
        assertFalse(repo.findById(bobNoti).orElseThrow().isRead());
    }

    @Test
    void E_전체읽음은_principal_알림만_바꾼다() throws Exception {
        mvc.perform(post("/api/notifications/read-all").param("to", BOB)
                        .header("Authorization", "Bearer " + token(ALICE)))
                .andExpect(status().isOk());
        assertTrue(repo.findById(aliceNoti).orElseThrow().isRead(), "앨리스 알림은 읽음");
        assertFalse(repo.findById(bobNoti).orElseThrow().isRead(), "밥 알림은 그대로");
    }

    @Test
    void F_미인증_요청은_모두_401이다() throws Exception {
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/notifications/" + aliceNoti + "/read")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/notifications/read-all")).andExpect(status().isUnauthorized());
    }
}
