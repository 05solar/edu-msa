package com.edu.msa.dbrouting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edu.msa.common.ProgramStatus;
import com.edu.msa.common.Scope;
import com.edu.msa.program.ProgramService;
import com.edu.msa.program.domain.Program;
import com.edu.msa.program.repository.ProgramRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * read replica 라우팅 검증.
 * primary(routedb)·replica(routero) 를 서로 다른 H2 DB 로 두고 primary 에만 데이터를 넣는다.
 * - replica 마킹 조회(list/counts) → replica(빈 DB)를 읽어 0건  → replica 라우팅 증명
 * - 비마킹 readOnly 조회(all/detail) → primary 를 읽어 데이터 조회 → read-after-write 경로 보존 증명
 * - 쓰기(save) → primary (이후 all 로 확인)
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:routedb;DB_CLOSE_DELAY=-1;MODE=MariaDB;DATABASE_TO_LOWER=TRUE",
        "edu.datasource.read-url=jdbc:h2:mem:routero;DB_CLOSE_DELAY=-1;MODE=MariaDB;DATABASE_TO_LOWER=TRUE",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "edu.seed=false",
})
@ActiveProfiles("test")
class ReadReplicaRoutingTest {

    @Autowired private ProgramService service;
    @Autowired private ProgramRepository programs;

    @BeforeAll
    static void migrateReplicaSchema() {
        // replica H2 에도 동일 스키마(V1)를 만든다 — 데이터는 넣지 않는다(라우팅 구분용)
        // 애플리케이션과 동일한 벤더 분리 위치를 명시한다(H2 → db/vendor/h2 동판)
        Flyway.configure()
                .dataSource("jdbc:h2:mem:routero;DB_CLOSE_DELAY=-1;MODE=MariaDB;DATABASE_TO_LOWER=TRUE", "sa", "")
                .locations("classpath:db/migration", "classpath:db/vendor/h2")
                .load().migrate();
    }

    private Program seedPrimary(String slug) {
        Program p = new Program();
        p.setName("라우팅 " + slug);
        p.setSlug(slug);
        p.setCat("doc");
        p.setOwner("tester");
        p.setStatus(ProgramStatus.PUBLIC);
        p.setScope(Scope.ALL);
        return programs.save(p);   // 쓰기 트랜잭션 → primary
    }

    @Test
    void replica_마킹된_카탈로그_조회는_replica_를_읽고_나머지는_primary_를_읽는다() {
        Program p = seedPrimary("route-check-svc");

        // 비마킹 readOnly — primary: 방금 쓴 데이터가 보인다(read-after-write 보존)
        assertTrue(service.all(0, 100).items().stream().anyMatch(x -> x.id().equals(p.getId())),
                "비마킹 readOnly(all)는 primary 를 읽어야 한다");
        assertEquals("라우팅 route-check-svc", service.detail(p.getId()).name(),
                "detail 은 primary 를 읽어야 한다(등록 직후 상세 진입 패턴)");

        // replica 마킹 — replica(빈 DB): 0건이면 실제로 replica 로 라우팅된 것
        assertEquals(0, service.list("doc", null, null, null, null, "latest", 0, 20).totalElements(),
                "list 는 replica 를 읽어야 한다(빈 replica → 0건)");
        assertTrue(service.publicCountsByCat().isEmpty(),
                "counts 는 replica 를 읽어야 한다(빈 replica → 빈 집계)");
    }
}
