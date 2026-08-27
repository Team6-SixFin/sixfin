package com.sparta.user.global.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sparta.user.global.exception.ErrorCode;
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
public class ErrorResponse {

    @JsonProperty("SUCCESS")
    @Builder.Default
    private final boolean success = false;

    private final String code;
    private final String message;
    private final Map<String, Object> details;

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
