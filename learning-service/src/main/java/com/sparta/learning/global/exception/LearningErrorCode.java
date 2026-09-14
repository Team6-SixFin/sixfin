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
    FEEDBACK_GENERATION_IN_PROGRESS(HttpStatus.CONFLICT, "피드백을 생성하고 있습니다."),
    POSITION_NOT_FOUND(HttpStatus.NOT_FOUND, "포지션을 찾을 수 없습니다."),
    FAILED_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "실패 이벤트를 찾을 수 없습니다."),
    FAILED_EVENT_ALREADY_RESOLVED(HttpStatus.CONFLICT, "이미 재처리된 이벤트입니다."),
    FAILED_EVENT_RETRY_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "실패 이벤트 재처리에 실패했습니다."),
    ADMIN_FORBIDDEN(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다."),

    POSITION_FIRST_TRADE_NOT_FOUND (HttpStatus.NOT_FOUND, "해당 포지션의 최초 체결 내역을 찾을 수 없습니다."),
    POSITION_LATEST_TRADE_NOT_FOUND (HttpStatus.NOT_FOUND, "해당 포지션의 최신 체결 내역을 찾을 수 없습니다."),
    EXECUTION_NOT_FOUND (HttpStatus.NOT_FOUND, "체결 내역을 찾을 수 없습니다."),
    CLOSED_POSITION_NOT_FOUND (HttpStatus.NOT_FOUND, "종료 포지션 최종 집계를 찾을 수 없습니다."),
    AI_RESPONSE_INCOMPLETE(HttpStatus.BAD_GATEWAY, "AI 응답 필수 필드가 누락되었습니다."),
    INVALID_AI_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 AI 제공자입니다."),
    AI_PROVIDER_OVERRIDE_FORBIDDEN(HttpStatus.FORBIDDEN, "AI 제공자 변경 권한이 없습니다."),
    AI_PROVIDER_OVERRIDE_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "AI 제공자 변경 기능이 설정되지 않았습니다."),
    AI_STUB_NOT_AVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Stub AI가 활성화되지 않았습니다."),
    AI_RESPONSE_GENERATION_FAILED(HttpStatus.BAD_GATEWAY, "AI 피드백 생성을 실패했습니다.");



    private final HttpStatus httpStatus;
    private final String message;
}
