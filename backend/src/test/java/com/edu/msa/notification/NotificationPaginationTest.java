package com.edu.msa.notification;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edu.msa.common.NotiKind;
import com.edu.msa.common.Role;
import com.edu.msa.notification.repository.NotificationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * P2-1 — 알림 서버 페이지네이션(전건 로드 제거) + 장기 보존 정책.
 * 정렬은 created_at DESC, id DESC(결정적) — 같은 시각 다건에서도 페이지 사이
 * 중복/누락이 없어야 한다. size 는 1~100 으로 강제, unread 는 DB COUNT 유지.
 * 보존: "읽은" 알림만 retention 경과 후 삭제(미읽음은 절대 자동 삭제 없음).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationPaginationTest {

    @Autowired private MockMvc mvc;
    @Autowired private NotificationService service;
    @Autowired private NotificationRepository repo;
    @Autowired private NotificationRetentionCleaner cleaner;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private javax.sql.DataSource dataSource;
    @Autowired private ObjectMapper mapper;
    @Value("${edu.jwt.secret}") private String secret;
    @Value("${edu.jwt.issuer}") private String issuer;

    private static final long UID_A = 9301L;
    private static final long UID_B = 9302L;

    private String token(long uid, String role) {
        return Jwts.builder()
                .issuer(issuer).subject("user-" + uid)
                .claim("uid", uid).claim("name", "페이지사용자" + uid).claim("dept", "테스트과")
                .claim("role", role).claim("typ", "access")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @BeforeEach
    void seed() {
        repo.deleteAll();
        // A 개인 알림 30건(같은 시각 다건 — created_at 동률 tie 상황 포함) + ADMIN 역할 공지 10건
        for (int i = 1; i <= 30; i++) {
            service.push(UID_A, "A", NotiKind.APPROVE, "A-" + i, "sub", null);
        }
        for (int i = 1; i <= 10; i++) {
            service.pushToRole(Role.ADMIN, "운영 관리자", NotiKind.SUBMIT, "공지-" + i, "sub", null);
        }
    }

    private JsonNode page(String tokenValue, String query) throws Exception {
        String body = mvc.perform(get("/api/notifications" + query)
                        .header("Authorization", "Bearer " + tokenValue))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    @Test
    void 기본_페이지는_20건_최신순이며_전건을_반환하지_않는다() throws Exception {
        JsonNode p0 = page(token(UID_A, "USER"), "");
        assertEquals(20, p0.get("items").size());
        assertEquals(30, p0.get("totalElements").asLong());
        assertEquals(2, p0.get("totalPages").asInt());
        assertEquals("A-30", p0.get("items").get(0).get("title").asText(), "최신(마지막 생성)이 먼저");
    }

    @Test
    void 페이지_사이_중복도_누락도_없다_동일시각_tie포함() throws Exception {
        Set<Long> ids = new HashSet<>();
        JsonNode p0 = page(token(UID_A, "USER"), "?page=0&size=20");
        JsonNode p1 = page(token(UID_A, "USER"), "?page=1&size=20");
        p0.get("items").forEach(n -> ids.add(n.get("id").asLong()));
        assertEquals(20, ids.size());
        p1.get("items").forEach(n -> assertTrue(ids.add(n.get("id").asLong()), "페이지 사이 중복 금지"));
        assertEquals(30, ids.size(), "두 페이지 합집합 = 전체(누락 없음)");
    }

    @Test
    void size는_상한_100으로_강제되고_비정상_값도_안전하다() throws Exception {
        assertEquals(100, page(token(UID_A, "USER"), "?size=1000000").get("size").asInt());
        assertEquals(1, page(token(UID_A, "USER"), "?size=0").get("size").asInt());
        assertEquals(1, page(token(UID_A, "USER"), "?size=-5").get("size").asInt());
        assertEquals(0, page(token(UID_A, "USER"), "?page=-3").get("page").asInt());
    }

    @Test
    void 역할_공지는_역할_수신자의_페이지에_개인알림과_함께_섞여_나온다() throws Exception {
        service.push(7777L, "관리자", NotiKind.APPROVE, "관리자개인", "sub", null);
        JsonNode p0 = page(token(7777L, "ADMIN"), "?size=100");
        assertEquals(11, p0.get("totalElements").asLong(), "개인 1 + 역할 공지 10");
    }

    @Test
    void 타인은_페이지_파라미터를_조작해도_아무것도_볼_수_없다() throws Exception {
        JsonNode p = page(token(UID_B, "USER"), "?page=0&size=100");
        assertEquals(0, p.get("totalElements").asLong(), "B(다른 uid)는 A 알림에 접근 불가");
    }

    // ---------- 보존 정책 ----------

    private void backdate(long uid, boolean read, int days, String title) throws Exception {
        service.push(uid, "A", NotiKind.APPROVE, title, "sub", null);
        try (var conn = dataSource.getConnection();
             var st = conn.prepareStatement(
                     "update notifications set created_at = ?, is_read = ? where title = ?")) {
            st.setObject(1, OffsetDateTime.now().minusDays(days));
            st.setBoolean(2, read);
            st.setString(3, title);
            assertEquals(1, st.executeUpdate());
        }
    }

    @Test
    void 보존정책은_오래된_읽음만_지우고_미읽음과_최근읽음은_보존한다() throws Exception {
        repo.deleteAll();
        backdate(UID_A, true, 120, "old-read");      // retention(90일) 경과 + 읽음 → 삭제 대상
        backdate(UID_A, false, 120, "old-unread");   // 경과했지만 미읽음 → 절대 삭제 금지
        backdate(UID_A, true, 10, "recent-read");    // 읽음이지만 retention 이내 → 보존
        backdate(UID_B, true, 120, "b-old-read");    // 사용자 무관하게 동일 정책

        int deleted = cleaner.cleanOnce();

        assertEquals(2, deleted, "오래된 읽음 2건(A,B)만 삭제");
        var titles = repo.findAll().stream().map(n -> n.getTitle()).toList();
        assertTrue(titles.contains("old-unread"), "미읽음은 자동 삭제하지 않는다");
        assertTrue(titles.contains("recent-read"), "retention 이내 읽음은 보존");
        assertTrue(!titles.contains("old-read") && !titles.contains("b-old-read"));
    }

    @Test
    void 정리_실패는_스케줄러를_죽이지_않는다() {
        NotificationRepository broken = mock(NotificationRepository.class);
        when(broken.deleteOldReadBatch(any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenThrow(new RuntimeException("db down"));
        NotificationRetentionCleaner c = new NotificationRetentionCleaner(broken, txManager);
        assertDoesNotThrow(c::tick, "정리 실패는 WARN 후 다음 주기 재시도여야 한다");
    }
}
