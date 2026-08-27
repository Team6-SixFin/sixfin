package com.sparta.trading.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TradingErrorCode implements  ErrorCode{

    //아래 예시를 에러상황에 맞게 고치고 복사해서 사용하면 됩니다.
    EXAMPLE_ERROR_CODE(HttpStatus.NOT_FOUND, "예시)존재 하지 않습니다");

    private final HttpStatus httpStatus;
    private final String message;
}
