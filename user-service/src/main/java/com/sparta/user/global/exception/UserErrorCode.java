package com.sparta.user.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements  ErrorCode{

    //아래 예시를 에러상황에 맞게 고치고 복사해서 사용하면 됩니다.
    EXAMPLE_ERROR_CODE(HttpStatus.NOT_FOUND, "예시)존재 하지 않습니다"),

    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "비밀번호가 일치하지 않습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
