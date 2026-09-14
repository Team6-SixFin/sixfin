package com.sparta.trading.infrastructure.clocks;

import com.sparta.trading.domain.entity.ClockStatus;
import com.sparta.trading.domain.entity.MarketClock;
import com.sparta.trading.domain.repository.clocks.MarketClockCommandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * market_clock의 단일 행(id=1)이 없으면 만든다. 매 기동 시 확인한다. 이미 있으면 항상 seq=0/STOPPED로 재설정.
 * MarketDataCsvLoader가 먼저 캔들을 채워야 start_seq 시각을 구할 수 있어 그 뒤에 실행되도록 순서를 고정한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(2)
public class MarketClockSeeder implements ApplicationRunner {

    static final int SINGLETON_ID = 1;

    private final MarketClockCommandRepository marketClockRepository;
    private final MarketClockProperties properties;
    private final MarketClockInitializer marketClockInitializer;

    @Override
    public void run(ApplicationArguments args) {
        if (marketClockRepository.existsById(SINGLETON_ID)) {
            // 행이 하나라도 있다면 이미 csv파일이 적재되어있음.
            MarketClock clock = marketClockInitializer.initializeClock(properties);
            log.info("[market-clock-seeder] 이미 시계행이 존재하여 현재 시간 기준으로 수정합니다. clock=[{}]", clock);
            return;
        }

        Instant anchorMarketTime = marketClockInitializer.resolveAnchorMarketTime(properties.startSeq());

        MarketClock marketClock = MarketClock.builder()
                .id(SINGLETON_ID)
                .anchorSeq(properties.startSeq())
                .anchorAt(Instant.now())
                .anchorMarketTime(anchorMarketTime)
                .startSeq(properties.startSeq())
                .endSeq(properties.endSeq())
                .speedFactor(properties.speedFactor())
                .cacheRefreshIntervalMs(properties.cacheRefreshIntervalMs())
                .status(ClockStatus.STOPPED)
                .build();

        marketClockRepository.save(marketClock);
        log.info("[market-clock-seeder] 시계 초기 행 생성 완료 - start_seq={}, end_seq={}",
                properties.startSeq(), properties.endSeq());
    }
}
