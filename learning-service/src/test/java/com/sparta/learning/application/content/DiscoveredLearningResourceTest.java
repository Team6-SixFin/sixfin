package com.sparta.learning.application.content;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscoveredLearningResourceTest {

    @Test
    void rejectsMissingRequiredFieldsAndNegativeMetrics() {
        assertThatThrownBy(() -> discovery("", "제목", "https://example.com", 10, 100L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> discovery("id", "", "https://example.com", 10, 100L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> discovery("id", "제목", "", 10, 100L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> discovery("id", "제목", "https://example.com", -1, 100L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> discovery("id", "제목", "https://example.com", 10, -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private DiscoveredLearningResource discovery(
            String externalId,
            String title,
            String url,
            Integer durationSeconds,
            Long viewCount
    ) {
        return new DiscoveredLearningResource(
                externalId,
                title,
                null,
                null,
                null,
                url,
                null,
                null,
                durationSeconds,
                viewCount
        );
    }
}
