package com.edu.msa.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edu.msa.common.PageResponse;
import com.edu.msa.common.ProgramStatus;
import com.edu.msa.common.Scope;
import com.edu.msa.program.ProgramService;
import com.edu.msa.program.domain.Program;
import com.edu.msa.program.dto.ProgramDtos.ProgramSummaryResponse;
import com.edu.msa.program.repository.ProgramRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카탈로그 캐시 동작 검증 — simple 캐시로 캐시 계층 로직만 확인한다(Redis 불필요).
 * 같은 키의 재조회는 DB 쿼리 없이 서빙되고, 데이터 변경 지점(evictor 배선)이 캐시를 무효화한다.
 */
@SpringBootTest(properties = {
        "spring.cache.type=simple",
        "spring.datasource.url=jdbc:h2:mem:cachedb;DB_CLOSE_DELAY=-1;MODE=MariaDB",
})
@ActiveProfiles("test")
@Transactional
class CatalogCacheTest {

    @Autowired private ProgramService service;
    @Autowired private ProgramRepository programs;
    @Autowired private CatalogCacheEvictor evictor;
    @PersistenceContext private EntityManager em;

    @BeforeEach
    void clearCaches() {
        evictor.evictAll();   // 캐시는 트랜잭션 롤백 대상이 아니므로 테스트 간 격리를 직접 한다
    }

    private Program seed(String slug) {
        Program p = new Program();
        p.setName("캐시 " + slug);
        p.setSlug(slug);
        p.setCat("doc");
        p.setOwner("tester");
        p.setStatus(ProgramStatus.PUBLIC);
        p.setScope(Scope.ALL);
        p.setCreatedAt(LocalDate.now());
        p.setUpdatedAt(LocalDate.now());
        return programs.save(p);
    }

    private Statistics stats() {
        return em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    }

    @Test
    void 같은_필터의_재조회는_DB쿼리_없이_캐시에서_서빙된다() {
        seed("cache-hit-a");
        seed("cache-hit-b");
        em.flush();

        Statistics st = stats();
        st.clear();
        PageResponse<ProgramSummaryResponse> first =
                service.list("doc", null, null, null, null, "latest", 0, 20);
        long firstQueries = st.getPrepareStatementCount();
        assertTrue(firstQueries > 0, "첫 조회는 DB 를 탄다");

        st.clear();
        PageResponse<ProgramSummaryResponse> second =
                service.list("doc", null, null, null, null, "latest", 0, 20);
        assertEquals(0, st.getPrepareStatementCount(), "같은 키의 재조회는 캐시 적중이어야 한다");
        assertEquals(first.totalElements(), second.totalElements());

        // 분야별 개수도 동일하게 캐시된다
        st.clear();
        service.publicCountsByCat();
        assertTrue(st.getPrepareStatementCount() > 0);
        st.clear();
        service.publicCountsByCat();
        assertEquals(0, st.getPrepareStatementCount());
    }

    @Test
    void 데이터_변경시_캐시가_즉시_무효화되어_최신을_반환한다() {
        Program p = seed("cache-evict-a");
        em.flush();

        long before = service.list("doc", null, null, null, null, "latest", 0, 20).totalElements();

        // delete() 에 배선된 evictor 가 캐시를 비우고, 재조회는 DB 에서 최신 상태를 읽는다
        service.delete(p.getId());
        em.flush();
        long after = service.list("doc", null, null, null, null, "latest", 0, 20).totalElements();

        assertEquals(before - 1, after, "변경 직후 조회에 stale 캐시가 아닌 최신 값이 반환되어야 한다");
    }
}
