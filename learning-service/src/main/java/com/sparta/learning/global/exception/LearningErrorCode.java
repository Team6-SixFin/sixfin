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
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "조회 시작일이 종료일보다 늦을 수 없습니다."),
    INVALID_FAILED_EVENT_STATUS(HttpStatus.BAD_REQUEST, "지원하지 않는 실패 이벤트 상태입니다."),
    INVALID_EVENT_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 이벤트 종류입니다."),
    FEEDBACK_NOT_FOUND(HttpStatus.NOT_FOUND, "피드백을 찾을 수 없습니다."),
    POSITION_NOT_FOUND(HttpStatus.NOT_FOUND, "포지션을 찾을 수 없습니다."),
    FAILED_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "실패 이벤트를 찾을 수 없습니다."),
    FAILED_EVENT_ALREADY_RESOLVED(HttpStatus.CONFLICT, "이미 재처리된 이벤트입니다."),
    FAILED_EVENT_PAYLOAD_BROKEN(HttpStatus.UNPROCESSABLE_ENTITY, "실패 이벤트 원본을 복원할 수 없습니다."),
    FAILED_EVENT_RETRY_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "실패 이벤트 재처리에 실패했습니다."),
    ADMIN_FORBIDDEN(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다."),

    POSITION_FIRST_TRADE_NOT_FOUND (HttpStatus.NOT_FOUND, "해당 포지션의 최초 체결 내역을 찾을 수 없습니다."),
    POSITION_LATEST_TRADE_NOT_FOUND (HttpStatus.NOT_FOUND, "해당 포지션의 최신 체결 내역을 찾을 수 없습니다."),
    EXECUTION_NOT_FOUND (HttpStatus.NOT_FOUND, "체결 내역을 찾을 수 없습니다."),
    CLOSED_POSITION_NOT_FOUND (HttpStatus.NOT_FOUND, "종료 포지션 최종 집계를 찾을 수 없습니다."),
    AI_RESPONSE_INCOMPLETE  (HttpStatus.NOT_FOUND, "AI 응답 필수 필드가 누락되었습니다."),


    AI_RESPONSE_GENERATION_FAILED(HttpStatus.NOT_FOUND, "AI 피드백 생성을 실패했습니다.");



    private final HttpStatus httpStatus;
    private final String message;
}
