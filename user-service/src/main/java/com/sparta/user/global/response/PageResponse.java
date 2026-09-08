package com.sparta.user.global.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

// Spring Data의 내부 Page 구조를 외부 API 명세에 맞춰 노출합니다.
public record PageResponse<T>(
        @Schema(description = "현재 페이지 데이터")
        List<T> content,
        @Schema(description = "현재 페이지 번호(0부터 시작)", example = "0")
        int page,
        @Schema(description = "페이지 크기", example = "20")
        int size,
        @Schema(description = "전체 데이터 수", example = "45")
        long totalElements,
        @Schema(description = "전체 페이지 수", example = "3")
        int totalPages,
        @Schema(description = "다음 페이지 존재 여부", example = "true")
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
