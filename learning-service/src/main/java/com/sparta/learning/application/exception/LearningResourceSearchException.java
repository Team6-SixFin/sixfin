package com.sparta.learning.application.exception;

/** 외부 학습 자료 검색 제공자와 통신하거나 응답을 변환하지 못했을 때 발생합니다. */
public class LearningResourceSearchException extends RuntimeException {

    public LearningResourceSearchException(String message) {
        super(message);
    }

    public LearningResourceSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
