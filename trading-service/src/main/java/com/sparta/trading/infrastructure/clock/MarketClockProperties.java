package com.sparta.trading.infrastructure.clock;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * market_clock 초기 시딩에만 쓰인다. 시딩 이후에는 DB 행(start_seq/end_seq 등)이 원본. 값을 코드가 아닌 yml로 관리
 */
@ConfigurationProperties(prefix = "market.clock")
public record MarketClockProperties(
        long startSeq,
        long endSeq,
        int speedFactor,
        int cacheRefreshIntervalMs
) {
}
