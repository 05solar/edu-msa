package com.edu.msa.review;

import com.edu.msa.review.dto.ReviewDtos.ReviewLogResponse;
import com.edu.msa.review.dto.ReviewDtos.ReviewRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    @PostMapping("/programs/{id}/review")
    public void review(@PathVariable Long id, @Valid @RequestBody ReviewRequest req) {
        service.review(id, req.action(), req.memo(), req.actor());
    }

    @GetMapping("/review/logs")
    public List<ReviewLogResponse> logs() {
        return service.logs();
    }
}
