package com.edu.msa;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edu.msa.program.repository.ProgramRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Flyway 마이그레이션 검증 — 빈 DB(H2 PostgreSQL 모드)에 V1 을 실제로 적용한 뒤
 * Hibernate `ddl-auto: validate` 가 엔티티 매핑과 스키마 일치를 확인하고,
 * 시드(DataSeeder)가 그 스키마에 실제 INSERT 까지 성공해야 컨텍스트가 뜬다.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.url=jdbc:h2:mem:flywaydb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "edu.seed=true",
})
@ActiveProfiles("test")
class SchemaMigrationTest {

    @Autowired private ProgramRepository programs;

    @Test
    void 마이그레이션_스키마가_엔티티와_일치하고_시드가_들어간다() {
        assertTrue(programs.count() > 0, "Flyway 스키마 위에서 시드 INSERT 까지 성공해야 한다");
    }
}
