package com.sparta.user.global.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sparta.user.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor(force = true)
@AllArgsConstructor
@Schema(description = "API 오류 응답")
public class ErrorResponse {

    @Schema(description = "요청 성공 여부. 오류 응답에서는 항상 false", example = "false")
    @JsonProperty("SUCCESS")
    @Builder.Default
    private final boolean success = false;

    @Schema(description = "오류 코드", example = "USER_NOT_FOUND")
    private final String code;

    @Schema(description = "사용자에게 전달할 오류 메시지", example = "존재하지 않는 사용자입니다.")
    private final String message;

    @Schema(description = "오류 원인 등 부가 정보", example = "{\"reason\": \"존재하지 않는 사용자입니다.\"}")
    private final Map<String, Object> details;

    @Schema(description = "오류 발생 시각", example = "2026-09-07 10:30:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private final LocalDateTime timestamp;

    public static ErrorResponse of(ErrorCode errorCode, String reason) {
        return ErrorResponse.builder()
                .code(errorCode.name())
                .message(errorCode.getMessage())
                .details(Map.of("reason", reason))
                .timestamp(LocalDateTime.now())
                .build();
    }
}
