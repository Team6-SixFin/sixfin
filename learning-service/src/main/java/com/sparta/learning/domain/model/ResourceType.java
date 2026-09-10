package com.sparta.learning.domain.model;

/**
 * 사용자에게 제공하는 학습 자료의 표현 형식입니다.
 * 제공처(provider)와 분리해 같은 제공처에서도 여러 형식의 자료를 다룰 수 있게 합니다.
 */
public enum ResourceType {
    VIDEO,
    DOCUMENT
}
