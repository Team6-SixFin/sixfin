package com.sparta.learning.global.exception;

import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 외부 API 호출이나 JSON 변환 실패의 근본 원인을 보존합니다.
     * 클라이언트에는 ErrorCode 메시지만 노출하고, 내부 로그와 실패 이력에서 cause를 추적합니다.
     */
    public CustomException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}
