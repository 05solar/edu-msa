package com.edu.msa.program.repository;

import com.edu.msa.common.ProgramStatus;
import com.edu.msa.program.domain.Program;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProgramRepository extends JpaRepository<Program, Long>, JpaSpecificationExecutor<Program> {
    List<Program> findByStatus(ProgramStatus status);
    Page<Program> findByStatus(ProgramStatus status, Pageable pageable);
    boolean existsBySlug(String slug);

    /** 카탈로그 사이드바용 분야별 개수 — 전체 행을 가져오지 않고 DB GROUP BY 로 집계한다. */
    @Query("select p.cat, count(p) from Program p where p.status = :status group by p.cat")
    List<Object[]> countByCatForStatus(@Param("status") ProgramStatus status);
}
