package com.sparta.learning.global.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Slice;

import java.util.List;

/**
 * count 쿼리 없이 내려주는 '사용자' 목록 응답.
 *
 * [Why totalElements / totalPages 제거] 전체 건수를 주려면 매 요청마다 조건에 맞는 모든 행을 세야 한다.
 * 이 count 쿼리에는 LIMIT이 없어 page=0을 조회해도 전체를 훑고, 동시 사용자 수만큼 그대로 누적된다.
 * 사용자 목록 화면이 실제로 필요한 건 '다음이 있는지'뿐이므로 전체 건수와 전체 페이지 수를 포기한다.
 *
 * [Trade-off] 총 페이지 수 표시가 불가능해진다. 무한 스크롤 또는 '더 보기' UI를 전제한다.
 *
 * [주의] 총 건수 자체가 업무 정보인 관리자 목록(실패 이벤트 등)에는 쓰지 말 것.
 *        그쪽은 PageResponse를 그대로 사용한다.
 */
@Schema(description = "사용자 목록 조회 결과")
public record SliceResponse<T>(

        @Schema(description = "현재 페이지에 포함된 데이터")
        List<T> content,

        @Schema(description = "페이징 정보")
        SliceInfo pageInfo
) {

    public static <T> SliceResponse<T> of(Slice<T> slice) {
        return new SliceResponse<>(slice.getContent(), SliceInfo.of(slice));
    }
}