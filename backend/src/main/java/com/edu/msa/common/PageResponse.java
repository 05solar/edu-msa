package com.edu.msa.common;

import java.util.List;
import org.springframework.data.domain.Page;

/** 목록 API 공통 페이지 응답 — 배열 전체 대신 페이지 단위로 반환한다. */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PageResponse<T> of(Page<T> p) {
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(),
                p.getTotalElements(), p.getTotalPages());
    }
}
