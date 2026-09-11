package com.sparta.trading.infrastructure.clocks;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * market_clock 시딩과, 매 기동 시 재초기화(MarketClockInitializer)에 공통으로 쓰인다.
 * 즉 이 yml 값이 기동할 때마다 DB 행에 다시 적용되는 원본이다.
 */
@ConfigurationProperties(prefix = "market.clock")
public record MarketClockProperties(
        long startSeq,
        long endSeq,
        int speedFactor,
        int cacheRefreshIntervalMs
) {
}
