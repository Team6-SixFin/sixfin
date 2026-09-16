package com.sparta.trading.infrastructure.monitoring;

import com.sparta.trading.domain.entity.ClockStatus;
import com.sparta.trading.domain.entity.MarketClockSnapshot;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Jackson3(tools.jackson) 기본 ObjectMapper가 java.time.Instant를
 * 별도 모듈 등록 없이 왕복 직렬화할 수 있는지 확인하는 검증용 테스트.
 * RedisConfig에서 JacksonJsonRedisSerializer로 MarketClockSnapshot을 캐싱하기로
 * 결정하기 전에, Instant 필드가 깨지지 않는지 확인하기 위해 작성함.
 */
class Jackson3InstantSmokeTest {

    @Test
    void roundTripsInstantFieldsWithPlainJackson3Mapper() {
        JsonMapper mapper = JsonMapper.builder().build();
        MarketClockSnapshot snapshot = new MarketClockSnapshot(
                100L,
                Instant.parse("2026-09-14T03:00:00Z"),
                Instant.parse("2026-08-04T16:55:00Z"),
                0L,
                7393L,
                1,
                1000,
                ClockStatus.RUNNING,
                Instant.parse("2026-09-14T03:00:05Z")
        );

        String json = mapper.writeValueAsString(snapshot);
        MarketClockSnapshot restored = mapper.readValue(json, MarketClockSnapshot.class);

        assertThat(restored).isEqualTo(snapshot);
        assertThat(restored.anchorAt()).isEqualTo(snapshot.anchorAt());
        assertThat(restored.anchorMarketTime()).isEqualTo(snapshot.anchorMarketTime());
    }
}
