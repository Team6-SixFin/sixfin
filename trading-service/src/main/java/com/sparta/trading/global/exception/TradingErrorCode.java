package com.sparta.trading.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TradingErrorCode implements  ErrorCode{

    //아래 예시를 에러상황에 맞게 고치고 복사해서 사용하면 됩니다.
    EXAMPLE_ERROR_CODE(HttpStatus.NOT_FOUND, "예시)존재 하지 않습니다"),

    MARKET_CLOCK_SEED_DATA_MISSING(HttpStatus.INTERNAL_SERVER_ERROR,
            "market_clock 시딩에 필요한 캔들 데이터가 없습니다. CSV 적재를 먼저 실행하세요."),

    MARKET_CLOCK_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "market_clock 행이 존재하지 않습니다."),
    MARKET_CLOCK_RUNNING(HttpStatus.CONFLICT, "재생 중에는 되감기를 할 수 없습니다. 먼저 정지하세요."),
    MARKET_CLOCK_SEQ_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "seq가 허용 범위를 벗어났습니다."),
    MARKET_CLOCK_INVALID_SPEED(HttpStatus.BAD_REQUEST, "배속은 1 이상이어야 합니다."),
    PRICE_CANDLE_NOT_FOUND_FOR_SEQ(HttpStatus.INTERNAL_SERVER_ERROR, "해당 seq의 캔들 데이터가 없습니다."),
    STOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 종목입니다."),
    MARKET_CLOCK_FORBIDDEN(HttpStatus.FORBIDDEN, "관리자만 호출할 수 있습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
