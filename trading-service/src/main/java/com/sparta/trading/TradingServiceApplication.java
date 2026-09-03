package com.sparta.trading;

import com.sparta.trading.infrastructure.clock.MarketClockProperties;
import com.sparta.trading.infrastructure.loader.MarketDataCsvProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({MarketDataCsvProperties.class, MarketClockProperties.class})
public class TradingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradingServiceApplication.class, args);
    }

}
