package com.edu.msa.review.dto;

import com.edu.msa.common.ReviewAction;
import jakarta.validation.constraints.NotNull;

public final class ReviewDtos {
    private ReviewDtos() {}

    public record ReviewRequest(
            @NotNull ReviewAction action,
            String memo,
            String actor
    ) {}

    public record ReviewLogResponse(
            String at, Long pid, String title, String by, ReviewAction act, String memo
    ) {}
}
