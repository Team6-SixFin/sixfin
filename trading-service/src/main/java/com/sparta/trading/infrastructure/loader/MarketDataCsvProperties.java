package com.sparta.trading.infrastructure.loader;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "market-data")
public record MarketDataCsvProperties(String csvDir) {
}
