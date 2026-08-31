package com.sparta.learning.infrastructure.persistence.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.auditing.DateTimeProvider;

import java.time.OffsetDateTime;
import java.time.temporal.TemporalAccessor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA Auditing이 BaseEntity.createdAt과 호환되는 OffsetDateTime을 제공하는지 검증합니다.
 */
class JpaAuditingConfigTest {

    // 기본 Provider가 LocalDateTime을 반환해 저장에 실패했던 문제의 재발을 방지합니다.
    @Test
    void providesUtcOffsetDateTime() {
        DateTimeProvider provider = new JpaAuditingConfig().auditingDateTimeProvider();

        TemporalAccessor now = provider.getNow().orElseThrow();

        assertThat(now).isInstanceOf(OffsetDateTime.class);
        assertThat(((OffsetDateTime) now).getOffset().getTotalSeconds()).isZero();
    }
}
