package com.edu.msa.notification;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edu.msa.common.NotiKind;
import com.edu.msa.common.Role;
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
 * P1-1(알림 IDOR 차단) + P1-5(불변 UID identity) — 수신 판정은 JWT principal 의
 * uid(recipient_id)로만 한다. **표시 이름이 완전히 같은 두 사용자(동명이인)** 사이에서도
 * 알림이 절대 섞이지 않아야 한다. 역할 공지(recipient_role)는 해당 역할만 본다.
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

    /** 동명이인 — 표시 이름은 완전히 같고 uid 만 다르다. */
    private static final String SAME_NAME = "김도현-p15";
    private static final long UID_A = 9101L;
    private static final long UID_B = 9102L;
    private Long aliceNoti;
    private Long bobNoti;

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

    @BeforeEach
    void seed() {
        repo.deleteAll();
        service.push(UID_A, SAME_NAME, NotiKind.APPROVE, "A 알림", "sub", null);
        service.push(UID_B, SAME_NAME, NotiKind.APPROVE, "B 알림", "sub", null);
        aliceNoti = repo.findAll().stream().filter(n -> UID_A == n.getRecipientId()).findFirst().orElseThrow().getId();
        bobNoti = repo.findAll().stream().filter(n -> UID_B == n.getRecipientId()).findFirst().orElseThrow().getId();
    }

    @Test
    void 동명이인이어도_목록은_uid_기준으로만_반환된다() throws Exception {
        mvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + token(UID_A, SAME_NAME, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("A 알림"));
    }

    @Test
    void unread_count도_uid_기준이다() throws Exception {
        service.push(UID_A, SAME_NAME, NotiKind.COMMENT, "A 알림 2", "sub", null);
        mvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + token(UID_A, SAME_NAME, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    void 동명이인의_알림_read는_404이고_상태가_변하지_않는다() throws Exception {
        mvc.perform(post("/api/notifications/" + bobNoti + "/read")
                        .header("Authorization", "Bearer " + token(UID_A, SAME_NAME, "USER")))
                .andExpect(status().isNotFound());
        assertFalse(repo.findById(bobNoti).orElseThrow().isRead(),
                "같은 표시 이름이어도 다른 uid 의 알림 상태가 변하면 안 된다");
        mvc.perform(post("/api/notifications/999999/read")
                        .header("Authorization", "Bearer " + token(UID_A, SAME_NAME, "USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void 본인_알림_read는_성공한다() throws Exception {
        mvc.perform(post("/api/notifications/" + aliceNoti + "/read")
                        .header("Authorization", "Bearer " + token(UID_A, SAME_NAME, "USER")))
                .andExpect(status().isOk());
        assertTrue(repo.findById(aliceNoti).orElseThrow().isRead());
        assertFalse(repo.findById(bobNoti).orElseThrow().isRead());
    }

    @Test
    void 전체읽음은_uid_알림만_바꾼다() throws Exception {
        mvc.perform(post("/api/notifications/read-all")
                        .header("Authorization", "Bearer " + token(UID_A, SAME_NAME, "USER")))
                .andExpect(status().isOk());
        assertTrue(repo.findById(aliceNoti).orElseThrow().isRead());
        assertFalse(repo.findById(bobNoti).orElseThrow().isRead(), "동명이인 B 알림은 그대로");
    }

    @Test
    void 역할_공지는_해당_역할에게만_보인다() throws Exception {
        service.pushToRole(Role.ADMIN, "운영 관리자", NotiKind.SUBMIT, "등록 요청 공지", "sub", null);
        mvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + token(7777L, "관리자", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.title=='등록 요청 공지')]").exists());
        mvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + token(UID_A, SAME_NAME, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));   // 자기 알림뿐, 관리자 공지 미포함
    }

    @Test
    void 이름이_바뀌어도_알림_접근은_유지된다() throws Exception {
        // 같은 uid, 표시 이름만 변경된 새 토큰 — display name 은 판정에 무관해야 한다
        mvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + token(UID_A, "개명한이름", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("A 알림"));
    }

    @Test
    void 미인증_요청은_모두_401이다() throws Exception {
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/notifications/" + aliceNoti + "/read")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/notifications/read-all")).andExpect(status().isUnauthorized());
    }
}
