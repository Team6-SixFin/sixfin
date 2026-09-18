package com.sparta.trading.presentation.dto.request;

import java.util.Map;

/**
 * payload가 null이면 기존 페이로드 그대로 재발행하고, 값이 있으면 덮어쓴 뒤 재발행한다.
 * Map으로 받는 이유: Spring Boot 4의 기본 HTTP 메시지 컨버터가 Jackson 3(tools.jackson)인데,
 * 엔티티가 쓰는 JsonNode는 Jackson 2(com.fasterxml.jackson)라 요청 바디에 직접 바인딩하면
 * "추상 타입이라 생성 못 함" 에러가 난다. Map은 두 Jackson 어느 쪽으로도 문제없이 바인딩되고,
 * JsonNode로의 변환은 서비스 계층에서 명시적으로 처리한다.
 */
public record TradingAdminRetryOutBoxRequest(
        Map<String, Object> payload
) {
}
