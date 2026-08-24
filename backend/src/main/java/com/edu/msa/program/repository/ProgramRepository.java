package com.edu.msa.program.repository;

import com.edu.msa.common.ProgramStatus;
import com.edu.msa.program.domain.Program;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgramRepository extends JpaRepository<Program, Long> {
    List<Program> findByStatus(ProgramStatus status);
    boolean existsBySlug(String slug);
}
