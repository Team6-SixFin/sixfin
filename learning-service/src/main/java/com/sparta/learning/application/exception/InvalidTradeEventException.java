package com.sparta.learning.application.exception;

/**
 * Trading이 발행한 이벤트가 Learning의 이벤트 계약을 만족하지 않을 때 발생합니다.
 * Listener에서 예외를 삼키지 않으므로 Kafka 컨테이너의 재시도 정책이 적용됩니다.
 */
public class InvalidTradeEventException extends RuntimeException {

    public InvalidTradeEventException(String message) {
        super(message);
    }

    public InvalidTradeEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
