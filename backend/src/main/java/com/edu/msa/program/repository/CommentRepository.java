package com.edu.msa.program.repository;

import com.edu.msa.program.domain.Comment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByProgramIdOrderByIdAsc(Long programId);
}
