package com.sparta.learning.global.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sparta.learning.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor(force = true)
@AllArgsConstructor
@Schema(description = "API 오류 응답")
public class ErrorResponse {

    @JsonProperty("SUCCESS")
    @Builder.Default
    @Schema(description = "요청 성공 여부. 오류 응답에서는 항상 false", example = "false")
    private final boolean success = false;

    @Schema(description = "클라이언트가 오류를 구분할 때 사용하는 코드", example = "FEEDBACK_NOT_FOUND")
    private final String code;

    @Schema(description = "사용자에게 표시할 수 있는 오류 메시지", example = "피드백을 찾을 수 없습니다.")
    private final String message;

    @Schema(description = "오류 원인과 추가 정보", example = "{\"reason\":\"피드백을 찾을 수 없습니다.\"}")
    private final Map<String, Object> details;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ssXXX")
    @Schema(description = "오류 발생 시각", example = "2026-09-07 10:55:01+09:00")
    private final OffsetDateTime timestamp;

    public static ErrorResponse of(ErrorCode errorCode, String reason) {
        return ErrorResponse.builder()
                .code(errorCode.name())
                .message(errorCode.getMessage())
                .details(Map.of("reason", reason))
                .timestamp(OffsetDateTime.now())
                .build();
    }
}
