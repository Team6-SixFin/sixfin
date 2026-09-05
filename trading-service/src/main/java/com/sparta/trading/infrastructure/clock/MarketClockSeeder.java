package com.sparta.trading.infrastructure.clock;

import com.sparta.trading.domain.entity.ClockStatus;
import com.sparta.trading.domain.entity.MarketClock;
import com.sparta.trading.domain.entity.PriceCandles;
import com.sparta.trading.infrastructure.persistence.repository.clocks.MarketClockRepository;
import com.sparta.trading.infrastructure.persistence.repository.candles.PriceCandlesRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * market_clock의 단일 행(id=1)이 없으면 만든다. 매 기동 시 확인한다. 이미 있으면 아무 것도 안 한다(멱등).
 * MarketDataCsvLoader가 먼저 캔들을 채워야 start_seq 시각을 구할 수 있어 그 뒤에 실행되도록 순서를 고정한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(2)
public class MarketClockSeeder implements ApplicationRunner {

    private static final int SINGLETON_ID = 1;

    private final MarketClockRepository marketClockRepository;
    private final PriceCandlesRepository priceCandlesRepository;
    private final MarketClockProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (marketClockRepository.existsById(SINGLETON_ID)) {
            log.info("[market-clock-seeder] 이미 시계 행이 존재하여 시딩을 건너뜁니다.");
            return;
        }

        Instant anchorMarketTime = priceCandlesRepository.findFirstBySeq(properties.startSeq())
                .map(PriceCandles::getMarketTime)
                .orElseThrow(() -> new CustomException(
                        TradingErrorCode.MARKET_CLOCK_SEED_DATA_MISSING,
                        "start-seq(%d)에 해당하는 캔들이 없습니다. CSV 적재를 먼저 실행하세요."
                                .formatted(properties.startSeq())));

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
