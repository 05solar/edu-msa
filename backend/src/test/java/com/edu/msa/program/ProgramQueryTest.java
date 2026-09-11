package com.edu.msa.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edu.msa.common.PageResponse;
import com.edu.msa.common.ProgramStatus;
import com.edu.msa.common.Scope;
import com.edu.msa.notification.NotificationService;
import com.edu.msa.common.NotiKind;
import com.edu.msa.common.Role;
import com.edu.msa.program.domain.Program;
import com.edu.msa.program.dto.ProgramDtos.ProgramSummaryResponse;
import com.edu.msa.program.repository.ProgramRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카탈로그 목록의 DB 단 필터/검색/정렬/페이지네이션과 N+1 제거 검증.
 * (test 프로파일 · H2 · hibernate.generate_statistics 로 쿼리 수 계측)
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProgramQueryTest {

    @Autowired private ProgramService service;
    @Autowired private ProgramRepository programs;
    @Autowired private NotificationService notifications;
    @PersistenceContext private EntityManager em;

    private Program seed(String name, ProgramStatus status, String cat, Scope scope,
                         List<String> purposes, List<String> tech, List<String> tags,
                         int views, int downloads, LocalDate updated) {
        Program p = new Program();
        p.setName(name);
        p.setSummary(name + " 요약");
        p.setDescription(name + " 설명");
        p.setCat(cat);
        p.setOwner("tester");
        p.setDept("부서");
        p.setVersion("1.0.0");
        p.setStatus(status);
        p.setScope(scope);
        p.setViews(views);
        p.setDownloads(downloads);
        p.setCreatedAt(updated);
        p.setUpdatedAt(updated);
        p.getPurposes().addAll(purposes);
        p.getTech().addAll(tech);
        p.getTags().addAll(tags);
        p.getRun().add("gitea");
        return programs.save(p);
    }

    @Test
    void 필터_검색_정렬이_DB쿼리로_기존과_같은_의미를_유지한다() {
        // 다른 테스트가 자체 커밋으로 남긴 데이터와 겹치지 않게 전용 cat 과 기준선 델타로 검증한다.
        long basePublic = service.list(null, null, null, null, null, "latest", 0, 1).totalElements();

        seed("큐공문서 검사기", ProgramStatus.PUBLIC, "qcat-doc", Scope.ALL,
                List.of("check", "convert"), List.of("QGo"), List.of("큐맞춤법"), 10, 5, LocalDate.of(2026, 1, 2));
        seed("큐데이터 요약기", ProgramStatus.PUBLIC, "qcat-data", Scope.ALL,
                List.of("check"), List.of("QPython"), List.of("큐통계"), 50, 1, LocalDate.of(2026, 1, 3));
        seed("큐부서용 도구", ProgramStatus.PUBLIC, "qcat-doc", Scope.DEPT,
                List.of("convert"), List.of("QJava"), List.of(), 5, 9, LocalDate.of(2026, 1, 1));
        seed("큐대기중 프로그램", ProgramStatus.PENDING, "qcat-doc", Scope.ALL,
                List.of("check"), List.of("QGo"), List.of(), 99, 99, LocalDate.of(2026, 1, 4));

        // 상태 필터: PUBLIC 3건만 추가로 잡힌다 (PENDING 제외)
        assertEquals(basePublic + 3, service.list(null, null, null, null, null, "latest", 0, 20).totalElements());
        // cat 필터
        assertEquals(2, service.list("qcat-doc", null, null, null, null, "latest", 0, 20).totalElements());
        // purposes containsAll (둘 다 가진 것만)
        PageResponse<ProgramSummaryResponse> both =
                service.list("qcat-doc", List.of("check", "convert"), null, null, null, "latest", 0, 20);
        assertEquals(1, both.totalElements());
        assertEquals("큐공문서 검사기", both.items().get(0).name());
        // tech 필터
        assertEquals(1, service.list(null, null, List.of("QPython"), null, null, "latest", 0, 20).totalElements());
        // scope 필터 (전용 cat 안에서)
        assertEquals(1, service.list("qcat-doc", null, null, "dept", null, "latest", 0, 20).totalElements());
        // 검색: 이름 부분 일치(대소문자 무시)
        assertEquals(1, service.list(null, null, null, null, "큐데이터", "latest", 0, 20).totalElements());
        // 검색: 태그로도 매칭
        assertEquals(1, service.list(null, null, null, null, "큐맞춤법", "latest", 0, 20).totalElements());
        // 정렬: popular = views desc (전용 cat 안에서: 10 > 5)
        assertEquals("큐공문서 검사기",
                service.list("qcat-doc", null, null, null, null, "popular", 0, 20).items().get(0).name());
        // 정렬: downloads desc
        assertEquals("큐부서용 도구",
                service.list("qcat-doc", null, null, null, null, "downloads", 0, 20).items().get(0).name());
        // 분야별 개수 집계
        Map<String, Long> counts = service.publicCountsByCat();
        assertEquals(2L, counts.get("qcat-doc"));
        assertEquals(1L, counts.get("qcat-data"));
    }

    @Test
    void 페이지네이션이_페이지단위로_동작한다() {
        for (int i = 1; i <= 25; i++) {
            seed("프로그램-" + i, ProgramStatus.PUBLIC, "qcat-page", Scope.ALL,
                    List.of("check"), List.of("Go"), List.of("t" + i), i, i, LocalDate.of(2026, 1, 1).plusDays(i));
        }
        PageResponse<ProgramSummaryResponse> p0 = service.list("qcat-page", null, null, null, null, "latest", 0, 10);
        assertEquals(10, p0.items().size());
        assertEquals(25, p0.totalElements());
        assertEquals(3, p0.totalPages());
        assertEquals("프로그램-25", p0.items().get(0).name());   // 최신순 첫 항목

        PageResponse<ProgramSummaryResponse> p2 = service.list("qcat-page", null, null, null, null, "latest", 2, 10);
        assertEquals(5, p2.items().size());
        assertEquals("프로그램-1", p2.items().get(4).name());    // 마지막 페이지 끝 = 가장 오래된 항목
    }

    @Test
    void 목록조회는_프로그램수와_무관하게_고정된_쿼리수로_수행된다() {
        for (int i = 1; i <= 25; i++) {
            seed("배치-" + i, ProgramStatus.PUBLIC, "doc", Scope.ALL,
                    List.of("check", "convert"), List.of("Go", "Python"), List.of("태그" + i),
                    i, i, LocalDate.of(2026, 1, 1).plusDays(i));
        }
        em.flush();
        em.clear();   // 1차 캐시 비우고 실제 로딩 쿼리를 계측한다

        Statistics stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        PageResponse<ProgramSummaryResponse> page = service.list(null, null, null, null, null, "latest", 0, 20);
        long statements = stats.getPrepareStatementCount();

        assertEquals(20, page.items().size());
        // 기대: 본문 1 + count 1 + 컬렉션 배치(tags/purposes/tech/run) 4 = 6.
        // 행 수(20)에 비례해 늘면 N+1 이 남아 있는 것이다.
        assertTrue(statements <= 7, "목록 조회 쿼리 수가 고정이어야 한다 (실제: " + statements + ")");
    }

    @Test
    void 알림_미읽음_카운트는_행을_가져오지_않고_DB_COUNT로_계산한다() {
        long uidA = 8801L;
        long uidB = 8802L;
        notifications.push(uidA, "사용자A", NotiKind.SUBMIT, "제목1", "부제", null);
        notifications.push(uidA, "사용자A", NotiKind.SUBMIT, "제목2", "부제", null);
        notifications.push(uidB, "사용자B", NotiKind.SUBMIT, "제목3", "부제", null);
        em.flush();
        em.clear();

        Statistics stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        assertEquals(2, notifications.unreadCount(uidA, Role.USER));
        assertEquals(1, stats.getPrepareStatementCount(), "COUNT 쿼리 1개로 처리되어야 한다");

        notifications.markAllRead(uidA, Role.USER);
        assertEquals(0, notifications.unreadCount(uidA, Role.USER));
        assertEquals(1, notifications.unreadCount(uidB, Role.USER));
    }
}
