package com.sparta.learning.global.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Spring Data의 내부 Page 구조를 외부 API 명세에 맞춰 노출합니다.
 */
@Schema(description = "페이지 조회 결과")
public record PageResponse<T>(
        @Schema(description = "현재 페이지에 포함된 데이터")
        List<T> content,

        @Schema(description = "현재 페이지 번호(0부터 시작)", example = "0")
        int page,

        @Schema(description = "페이지당 데이터 수", example = "20")
        int size,

        @Schema(description = "조건에 맞는 전체 데이터 수", example = "42")
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
