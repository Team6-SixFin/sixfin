package com.sparta.learning.global.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Slice;

/**
 * 사용자 목록 조회의 페이징 메타데이터.
 *
 * [Why paginationType] 지금은 offset 기반이라 뒤 페이지에서 건너뛴 행을 읽는 비용이 남아 있다.
 * 측정 후 커서 방식으로 전환할 여지가 있는데, 이 필드가 있으면 클라이언트가 방식으로 분기할 수 있어
 * 전환이 필드 '제거'가 아니라 '추가'로 끝난다.
 */
@Schema(description = "목록 페이징 정보")
public record SliceInfo(

        @Schema(description = "페이징 방식", example = "OFFSET", allowableValues = {"OFFSET", "CURSOR"})
        String paginationType,

        @Schema(description = "현재 페이지 번호(0부터 시작)", example = "0")
        int page,

        @Schema(description = "페이지당 데이터 수", example = "20")
        int size,

        @Schema(description = "다음 페이지 존재 여부", example = "true")
        boolean hasNext
) {

    private static final String OFFSET = "OFFSET";

    public static SliceInfo of(Slice<?> slice) {
        return new SliceInfo(OFFSET, slice.getNumber(), slice.getSize(), slice.hasNext());
    }
}