package com.sparta.trading.application.service;

import com.sparta.trading.domain.entity.ClockStatus;
import com.sparta.trading.domain.entity.MarketClock;
import com.sparta.trading.domain.entity.PriceCandles;
import com.sparta.trading.infrastructure.persistence.repository.candles.PriceCandlesRepository;
import com.sparta.trading.infrastructure.persistence.repository.clocks.MarketClockRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketClockCommandServiceTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-03T13:30:00Z");

    @Mock
    private MarketClockRepository marketClockRepository;

    @Mock
    private PriceCandlesRepository priceCandlesRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private MarketClockCommandService service;

    private MarketClock clockOf(long anchorSeq, Instant anchorAt, int speedFactor, ClockStatus status) {
        return MarketClock.builder()
                .id(1)
                .anchorSeq(anchorSeq)
                .anchorAt(anchorAt)
                .anchorMarketTime(BASE_TIME.plusSeconds(anchorSeq * 60))
                .startSeq(0L)
                .endSeq(7393L)
                .speedFactor(speedFactor)
                .cacheRefreshIntervalMs(1000)
                .status(status)
                .build();
    }

    private void givenClock(MarketClock marketClock) {
        when(marketClockRepository.findForUpdate()).thenReturn(Optional.of(marketClock));
    }

    private void givenCandleAt(long seq) {
        PriceCandles candle = PriceCandles.builder()
                .seq(seq)
                .marketTime(BASE_TIME.plusSeconds(seq * 60))
                .build();
        when(priceCandlesRepository.findFirstBySeq(seq)).thenReturn(Optional.of(candle));
    }

    @Test
    @DisplayName("start: 정지된 위치에서 재생을 시작하면 그 위치가 새 앵커가 된다")
    void start_setsAnchorToCurrentPositionAndRunning() {
        MarketClock marketClock = clockOf(0L, BASE_TIME, 1, ClockStatus.STOPPED);
        givenClock(marketClock);
        Instant startedAt = BASE_TIME.plusSeconds(100);
        when(clock.instant()).thenReturn(startedAt);
        givenCandleAt(0L);

        MarketClock result = service.start();

        assertThat(result.getAnchorSeq()).isEqualTo(0L);
        assertThat(result.getAnchorAt()).isEqualTo(startedAt);
        assertThat(result.getStatus()).isEqualTo(ClockStatus.RUNNING);
    }

    @Test
    @DisplayName("n초가 지난 뒤 조회하면 speedFactor배만큼 seq가 전진해 있다")
    void currentSeq_advancesBySpeedFactorAfterElapsedSeconds() {
        MarketClock marketClock = clockOf(0L, BASE_TIME, 1, ClockStatus.RUNNING);

        long seqAfter5Seconds = marketClock.currentSeq(BASE_TIME.plusSeconds(5));

        assertThat(seqAfter5Seconds).isEqualTo(5L);
    }

    @Test
    @DisplayName("stop: 재생 중인 위치를 그 자리에 고정한다")
    void stop_freezesAtCurrentComputedSeq() {
        MarketClock marketClock = clockOf(1000L, BASE_TIME, 10, ClockStatus.RUNNING);
        givenClock(marketClock);
        Instant stoppedAt = BASE_TIME.plusSeconds(10);
        when(clock.instant()).thenReturn(stoppedAt);
        givenCandleAt(1100L);

        MarketClock result = service.stop();

        assertThat(result.getAnchorSeq()).isEqualTo(1100L);
        assertThat(result.getStatus()).isEqualTo(ClockStatus.STOPPED);
    }

    @Test
    @DisplayName("stop 이후에는 시간이 아무리 지나도 seq가 그대로다")
    void afterStop_currentSeqStaysConstant() {
        MarketClock marketClock = clockOf(1100L, BASE_TIME, 10, ClockStatus.STOPPED);

        long seqSoonAfter = marketClock.currentSeq(BASE_TIME.plusSeconds(1));
        long seqMuchLater = marketClock.currentSeq(BASE_TIME.plusSeconds(100_000));

        assertThat(seqSoonAfter).isEqualTo(1100L);
        assertThat(seqMuchLater).isEqualTo(1100L);
    }

    @Test
    @DisplayName("resume: 정지 위치에서 재시작하면 정지해 있던 시간은 계산에 포함되지 않는다")
    void resume_doesNotCountElapsedTimeWhileStopped() {
        MarketClock marketClock = clockOf(1100L, BASE_TIME, 10, ClockStatus.STOPPED);
        givenClock(marketClock);
        Instant resumedAt = BASE_TIME.plusSeconds(100_000); // 정지 상태로 오래 방치된 뒤 재시작
        when(clock.instant()).thenReturn(resumedAt);
        givenCandleAt(1100L);

        service.start();
        long seqRightAfterResume = marketClock.currentSeq(resumedAt);
        long seqFiveSecondsAfterResume = marketClock.currentSeq(resumedAt.plusSeconds(5));

        assertThat(seqRightAfterResume).isEqualTo(1100L);
        assertThat(seqFiveSecondsAfterResume).isEqualTo(1150L); // 1100 + 5초 × 10배속
    }

    @Test
    @DisplayName("speed: 배속을 바꾸는 순간까지는 옛 배속으로, 이후는 새 배속으로 계산된다")
    void changeSpeed_reanchorsBeforeApplyingNewSpeed() {
        MarketClock marketClock = clockOf(0L, BASE_TIME, 1, ClockStatus.RUNNING);
        givenClock(marketClock);
        Instant changedAt = BASE_TIME.plusSeconds(10); // 옛 배속(1)로 10초 지남 -> seq 10
        when(clock.instant()).thenReturn(changedAt);
        givenCandleAt(10L);

        service.changeSpeed(10);

        assertThat(marketClock.getAnchorSeq()).isEqualTo(10L);
        assertThat(marketClock.getSpeedFactor()).isEqualTo(10);
        long seqFiveSecondsLater = marketClock.currentSeq(changedAt.plusSeconds(5));
        assertThat(seqFiveSecondsLater).isEqualTo(60L); // 10 + 5초 × 10배속
    }

    @Test
    @DisplayName("reset: 정지 상태에서 지정한 seq로 정확히 이동한다")
    void reset_movesToExactTargetSeqWhileStopped() {
        MarketClock marketClock = clockOf(1000L, BASE_TIME, 1, ClockStatus.STOPPED);
        givenClock(marketClock);
        Instant resetAt = BASE_TIME.plusSeconds(500);
        when(clock.instant()).thenReturn(resetAt);
        givenCandleAt(200L);

        MarketClock result = service.reset(200L);

        assertThat(result.getAnchorSeq()).isEqualTo(200L);
        assertThat(result.getStatus()).isEqualTo(ClockStatus.STOPPED);
    }

    @Test
    @DisplayName("reset: 재생 중에는 거부된다")
    void reset_rejectedWhileRunning() {
        MarketClock marketClock = clockOf(1000L, BASE_TIME, 1, ClockStatus.RUNNING);
        givenClock(marketClock);

        CustomException exception = assertThrows(CustomException.class, () -> service.reset(200L));

        assertThat(exception.getErrorCode()).isEqualTo(TradingErrorCode.MARKET_CLOCK_RUNNING);
    }

    @Test
    @DisplayName("reset: 허용 범위를 벗어난 seq는 거부된다")
    void reset_rejectedWhenSeqOutOfRange() {
        MarketClock marketClock = clockOf(1000L, BASE_TIME, 1, ClockStatus.STOPPED);
        givenClock(marketClock);

        CustomException exception = assertThrows(CustomException.class, () -> service.reset(99999L));

        assertThat(exception.getErrorCode()).isEqualTo(TradingErrorCode.MARKET_CLOCK_SEQ_OUT_OF_RANGE);
    }

    @Test
    @DisplayName("speed: 1 미만의 배속은 거부된다")
    void changeSpeed_rejectedWhenBelowOne() {
        CustomException exception = assertThrows(CustomException.class, () -> service.changeSpeed(0));

        assertThat(exception.getErrorCode()).isEqualTo(TradingErrorCode.MARKET_CLOCK_INVALID_SPEED);
    }

    @Test
    @DisplayName("market_clock 행이 없으면 조회 실패로 처리된다")
    void start_failsWhenClockRowMissing() {
        when(marketClockRepository.findForUpdate()).thenReturn(Optional.empty());

        CustomException exception = assertThrows(CustomException.class, () -> service.start());

        assertThat(exception.getErrorCode()).isEqualTo(TradingErrorCode.MARKET_CLOCK_NOT_FOUND);
    }

    @Test
    @DisplayName("해당 seq에 캔들 데이터가 없으면 실패로 처리된다")
    void start_failsWhenCandleMissingForSeq() {
        MarketClock marketClock = clockOf(0L, BASE_TIME, 1, ClockStatus.STOPPED);
        givenClock(marketClock);
        when(clock.instant()).thenReturn(BASE_TIME);
        when(priceCandlesRepository.findFirstBySeq(0L)).thenReturn(Optional.empty());

        CustomException exception = assertThrows(CustomException.class, () -> service.start());

        assertThat(exception.getErrorCode()).isEqualTo(TradingErrorCode.PRICE_CANDLE_NOT_FOUND_FOR_SEQ);
    }

    @Test
    @DisplayName("끝 seq 근처로 건너뛴 뒤 재생하면 end_seq에서 더 나아가지 않는다")
    void currentSeq_neverExceedsEndSeq() {
        MarketClock marketClock = clockOf(7390L, BASE_TIME, 100, ClockStatus.RUNNING);

        long seqFarInFuture = marketClock.currentSeq(BASE_TIME.plusSeconds(100));

        assertThat(seqFarInFuture).isEqualTo(7393L); // endSeq로 클램프, 절대 넘지 않음
    }

    @Test
    @DisplayName("autoStop: RUNNING 중 end_seq에 도달하면 STOPPED로 전이하고 앵커를 end_seq에 고정한다")
    void autoStopIfReached_stopsAtEndSeqWhenReached() {
        MarketClock marketClock = clockOf(7390L, BASE_TIME, 100, ClockStatus.RUNNING);
        givenClock(marketClock);
        Instant checkedAt = BASE_TIME.plusSeconds(100);
        when(clock.instant()).thenReturn(checkedAt);
        givenCandleAt(7393L);

        service.autoStopIfReached();

        assertThat(marketClock.getAnchorSeq()).isEqualTo(7393L);
        assertThat(marketClock.getAnchorAt()).isEqualTo(checkedAt);
        assertThat(marketClock.getStatus()).isEqualTo(ClockStatus.STOPPED);
    }

    @Test
    @DisplayName("autoStop: 아직 end_seq에 도달하지 않았으면 아무 것도 하지 않는다")
    void autoStopIfReached_noopWhenNotReached() {
        MarketClock marketClock = clockOf(0L, BASE_TIME, 1, ClockStatus.RUNNING);
        givenClock(marketClock);
        when(clock.instant()).thenReturn(BASE_TIME.plusSeconds(5));

        service.autoStopIfReached();

        assertThat(marketClock.getAnchorSeq()).isEqualTo(0L);
        assertThat(marketClock.getStatus()).isEqualTo(ClockStatus.RUNNING);
    }

    @Test
    @DisplayName("autoStop: 이미 STOPPED면 아무 것도 하지 않는다 (멱등)")
    void autoStopIfReached_noopWhenAlreadyStopped() {
        MarketClock marketClock = clockOf(7393L, BASE_TIME, 100, ClockStatus.STOPPED);
        givenClock(marketClock);
        when(clock.instant()).thenReturn(BASE_TIME.plusSeconds(100));

        service.autoStopIfReached();

        assertThat(marketClock.getAnchorAt()).isEqualTo(BASE_TIME);
        assertThat(marketClock.getStatus()).isEqualTo(ClockStatus.STOPPED);
    }
}
