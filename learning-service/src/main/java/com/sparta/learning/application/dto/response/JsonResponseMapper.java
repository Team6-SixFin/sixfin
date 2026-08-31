package com.sparta.learning.application.dto.response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Hibernate JSONB에서 사용하는 Jackson 2 JsonNode를 API가 안정적으로 직렬화할 수 있는 Map으로 변환
 */
final class JsonResponseMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private JsonResponseMapper() {
    }

    static Map<String, Object> toMap(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (!node.isObject()) {
            return Map.of();
        }

        return OBJECT_MAPPER.convertValue(node, MAP_TYPE);
    }
}
