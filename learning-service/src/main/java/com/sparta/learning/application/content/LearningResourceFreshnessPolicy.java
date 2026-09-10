package com.sparta.learning.application.content;

import com.sparta.learning.domain.model.ResourceType;
import com.sparta.learning.infrastructure.config.LearningResourceFreshnessProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/** 자료 유형에 따라 DB 후보로 사용할 수 있는 유효기간을 계산합니다. */
@Component
@RequiredArgsConstructor
public class LearningResourceFreshnessPolicy {

    private final LearningResourceFreshnessProperties properties;

    public OffsetDateTime expiresAt(ResourceType resourceType, OffsetDateTime refreshedAt) {
        return switch (resourceType) {
            case VIDEO -> refreshedAt.plus(properties.getVideoTtl());
            case DOCUMENT -> refreshedAt.plus(properties.getDocumentTtl());
        };
    }
}
