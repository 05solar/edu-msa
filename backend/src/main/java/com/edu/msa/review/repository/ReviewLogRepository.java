package com.edu.msa.review.repository;

import com.edu.msa.review.domain.ReviewLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewLogRepository extends JpaRepository<ReviewLog, Long> {
    List<ReviewLog> findAllByOrderByIdDesc();
}
