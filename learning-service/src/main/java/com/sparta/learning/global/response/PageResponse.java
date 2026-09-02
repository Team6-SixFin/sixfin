package com.sparta.learning.global.response;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Spring Data의 내부 Page 구조를 외부 API 명세에 맞춰 노출합니다.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public static <T> PageResponse<T> from(Page<?> source, List<T> content) {
        return new PageResponse<>(
                content,
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages(),
                source.hasNext()
        );
    }
}
