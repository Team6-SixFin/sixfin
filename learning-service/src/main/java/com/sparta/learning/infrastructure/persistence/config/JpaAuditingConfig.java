package com.sparta.learning.infrastructure.persistence.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * BaseEntity의 생성 시각 타입인 OffsetDateTime에 맞춰 JPA Auditing 시각을 제공합니다.
 * 서버가 어느 타임존에서 실행되더라도 DB에는 동일한 기준 시각이 저장되도록 UTC를 사용합니다.
 */
@Configuration
public class JpaAuditingConfig {

    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now(ZoneOffset.UTC));
    }
}
