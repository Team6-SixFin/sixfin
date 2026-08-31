package com.sparta.trading.presentation.controller;

import com.sparta.trading.application.dto.command.ChangeSpeedRequest;
import com.sparta.trading.application.dto.command.MarketClockInternalResponse;
import com.sparta.trading.application.dto.command.ResetClockRequest;
import com.sparta.trading.application.service.MarketClockCommandService;
import com.sparta.trading.domain.entity.MarketClock;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 서비스 간 호출용 내부 API. 실제 로직은 MarketClockCommandService에 위임한다. */
@RestController
@RequestMapping("/api/trading/internal/market/clock")
@RequiredArgsConstructor
public class TradingInternalController {

    private final MarketClockCommandService marketClockCommandService;

    @PostMapping("/start")
    public MarketClockInternalResponse start() {
        MarketClock clock = marketClockCommandService.start();
        return MarketClockInternalResponse.fromAnchor(clock);
    }

    @PostMapping("/stop")
    public MarketClockInternalResponse stop() {
        MarketClock clock = marketClockCommandService.stop();
        return MarketClockInternalResponse.fromAnchor(clock);
    }

    @PostMapping("/speed")
    public MarketClockInternalResponse changeSpeed(@Valid @RequestBody ChangeSpeedRequest request) {
        MarketClock clock = marketClockCommandService.changeSpeed(request.speedFactor());
        return MarketClockInternalResponse.fromAnchor(clock);
    }

    @PostMapping("/reset")
    public MarketClockInternalResponse reset(@Valid @RequestBody ResetClockRequest request) {
        MarketClock clock = marketClockCommandService.reset(request.seq());
        return MarketClockInternalResponse.fromAnchor(clock);
    }
}
