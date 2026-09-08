package com.sparta.learning.application.content;

import com.sparta.learning.domain.model.ResourceType;
import com.sparta.learning.infrastructure.config.LearningResourceFreshnessProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LearningResourceFreshnessPolicyTest {

    @Test
    void appliesDifferentFreshnessPeriodsByResourceType() {
        LearningResourceFreshnessProperties properties = new LearningResourceFreshnessProperties();
        properties.setVideoTtl(Duration.ofDays(7));
        properties.setDocumentTtl(Duration.ofDays(30));
        LearningResourceFreshnessPolicy policy = new LearningResourceFreshnessPolicy(properties);
        OffsetDateTime refreshedAt = OffsetDateTime.of(2026, 9, 8, 0, 0, 0, 0, ZoneOffset.UTC);

        assertThat(policy.expiresAt(ResourceType.VIDEO, refreshedAt))
                .isEqualTo(refreshedAt.plusDays(7));
        assertThat(policy.expiresAt(ResourceType.DOCUMENT, refreshedAt))
                .isEqualTo(refreshedAt.plusDays(30));
    }
}
