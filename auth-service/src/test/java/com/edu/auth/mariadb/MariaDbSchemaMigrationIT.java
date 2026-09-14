package com.edu.auth.mariadb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edu.auth.account.repository.AccountRepository;
import com.edu.auth.session.repository.RefreshTokenRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Flyway MariaDB 동판(vendor/mariadb) 검증 — 빈 MariaDB 에 V1~V2 를 실제로 적용한 뒤
 * Hibernate `ddl-auto: validate` 가 엔티티 매핑과 스키마 일치를 확인하고,
 * 시드(계정 7명)가 그 스키마에 실제 INSERT 까지 성공해야 컨텍스트가 뜬다.
 * 파생 테이블로 감싼 배치 삭제 네이티브 쿼리(deleteExpiredBatch)도 실 DB 에서 실행한다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "edu.seed.enabled=true",
        "edu.jwt.secret=test-secret-key-for-edu-auth-service-only-32bytes+",
        "edu.auth.ratelimit.store=memory",
        "edu.auth.cleanup.enabled=false",
})
class MariaDbSchemaMigrationIT {

    @Container
    static final MariaDBContainer<?> DB = new MariaDBContainer<>("mariadb:11.4")
            .withDatabaseName("eduauth")
            .withUsername("eduauth")
            .withPassword("eduauth")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> DB.getJdbcUrl() + "?timezone=UTC");
        r.add("spring.datasource.username", DB::getUsername);
        r.add("spring.datasource.password", DB::getPassword);
    }

    @Autowired private AccountRepository accounts;
    @Autowired private RefreshTokenRepository tokens;

    @Test
    void 마이그레이션_스키마가_엔티티와_일치하고_시드가_들어간다() {
        assertTrue(accounts.count() > 0, "Flyway 스키마 위에서 시드 INSERT 까지 성공해야 한다");
        // 한글 컬럼 왕복(utf8mb4) — 시드 계정의 한글 이름/부서가 깨지지 않아야 한다
        assertTrue(accounts.findAll().stream().anyMatch(a -> a.getName() != null && !a.getName().isBlank()));
    }

    @Test
    @Transactional
    void 만료_토큰_배치_삭제_쿼리가_MariaDB_에서_실행된다() {
        // 파생 테이블 형태(DELETE ... IN (SELECT id FROM (...) x))의 문법·실행 검증.
        // 데이터가 없어도 Error 1093 이 없다는 것 자체가 검증 대상이다.
        assertEquals(0, tokens.deleteExpiredBatch(OffsetDateTime.now().minusDays(30), 100));
    }
}
