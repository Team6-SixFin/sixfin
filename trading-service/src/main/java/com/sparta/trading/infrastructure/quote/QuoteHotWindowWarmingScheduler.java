package com.sparta.trading.infrastructure.quote;

import com.sparta.trading.application.service.CurrentSeqProvider;
import com.sparta.trading.domain.entity.ClockStatus;
import com.sparta.trading.domain.entity.MarketClockSnapshot;
import com.sparta.trading.domain.entity.PriceCandles;
import com.sparta.trading.infrastructure.persistence.repository.candles.PriceCandlesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class QuoteHotWindowWarmingScheduler {

    private static final long SCHEDULER_DELAY_IN_MILLISECONDS = 1000L;
    private static final long EXPIRE_TTL_DELAY_IN_MILLISECONDS = 3000L;

    private final PriceCandlesRepository priceCandlesRepository;
    private final CurrentSeqProvider currentSeqProvider;

    private final RedisTemplate<String, QuoteWindowSnapshot> quoteRedisTemplate;

    @Scheduled(fixedDelay = SCHEDULER_DELAY_IN_MILLISECONDS)
    public void cacheQuoteData(){
        MarketClockSnapshot clockSnapshot = currentSeqProvider.getClockSnapshot();

        if(ClockStatus.STOPPED.equals(clockSnapshot.status())){
            return;
        }

        long windowSize = clockSnapshot.speedFactor() * (SCHEDULER_DELAY_IN_MILLISECONDS / 1000L);
        long currentSeq = clockSnapshot.currentSeq(currentSeqProvider.now());
        long endSeq = currentSeq + windowSize;

        Map<Long, List<PriceCandles>> candlesBySeq = priceCandlesRepository.findAllBySeqBetween(
                currentSeq, endSeq).stream()
                .collect(Collectors.groupingBy(PriceCandles::getSeq));

        Instant now = currentSeqProvider.now();
        ValueOperations<String, QuoteWindowSnapshot> ops = quoteRedisTemplate.opsForValue();
        for (long seq = currentSeq; seq <= endSeq; seq++) {
            List<PriceCandles> candles = candlesBySeq.get(seq);
            if (candles == null || candles.isEmpty()) {
                continue;
            }
            ops.set("price:%d".formatted(seq),
                    QuoteWindowSnapshot.from(seq, candles, clockSnapshot, now),
                    Duration.ofMillis(EXPIRE_TTL_DELAY_IN_MILLISECONDS));
        }
    }
}
