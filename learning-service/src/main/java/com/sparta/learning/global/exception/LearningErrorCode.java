package com.sparta.learning.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum LearningErrorCode implements ErrorCode {

    INVALID_FEEDBACK_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 피드백 종류입니다."),
    INVALID_FEEDBACK_STATUS(HttpStatus.BAD_REQUEST, "지원하지 않는 피드백 상태입니다."),
    INVALID_PAGE_REQUEST(HttpStatus.BAD_REQUEST, "페이지 번호와 크기를 확인해주세요."),
    FEEDBACK_NOT_FOUND(HttpStatus.NOT_FOUND, "피드백을 찾을 수 없습니다."),
    POSITION_NOT_FOUND(HttpStatus.NOT_FOUND, "포지션을 찾을 수 없습니다."),

    POSITION_FIRST_TRADE_NOT_FOUND (HttpStatus.NOT_FOUND, "해당 포지션의 최초 체결 내역을 찾을 수 없습니다."),
    POSITION_LATEST_TRADE_NOT_FOUND (HttpStatus.NOT_FOUND, "해당 포지션의 최신 체결 내역을 찾을 수 없습니다."),

    AI_RESPONSE_GENERATION_FAILED(HttpStatus.NOT_FOUND, "AI 피드백 생성을 실패했습니다.");



    private final HttpStatus httpStatus;
    private final String message;
}
