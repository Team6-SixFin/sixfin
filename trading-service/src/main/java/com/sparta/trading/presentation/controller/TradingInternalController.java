package com.sparta.trading.presentation.controller;

import com.sparta.trading.application.dto.command.ChangeSpeedRequest;
import com.sparta.trading.presentation.dto.response.MarketClockInternalResponse;
import com.sparta.trading.application.dto.command.ResetClockRequest;
import com.sparta.trading.application.service.MarketClockCommandService;
import com.sparta.trading.domain.entity.MarketClock;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 서비스 간 호출용 내부 API. 실제 로직은 MarketClockCommandService에 위임한다.
 * AdminAuthorizationInterceptor가 이 경로 전체에 ROLE_ADMIN을 요구한다.
 */
@RestController
@RequestMapping("/api/trading/internal/market/clock")
@RequiredArgsConstructor
public class TradingInternalController {

    private final MarketClockCommandService marketClockCommandService;

    @PostMapping("/start")
    public MarketClockInternalResponse start(@RequestHeader("X-User-Id") UUID userId) {
        MarketClock clock = marketClockCommandService.start(userId);
        return MarketClockInternalResponse.fromAnchor(clock);
    }

    @PostMapping("/stop")
    public MarketClockInternalResponse stop(@RequestHeader("X-User-Id") UUID userId) {
        MarketClock clock = marketClockCommandService.stop(userId);
        return MarketClockInternalResponse.fromAnchor(clock);
    }

    @PostMapping("/speed")
    public MarketClockInternalResponse changeSpeed(@Valid @RequestBody ChangeSpeedRequest request,
                                                    @RequestHeader("X-User-Id") UUID userId) {
        MarketClock clock = marketClockCommandService.changeSpeed(request.speedFactor(), userId);
        return MarketClockInternalResponse.fromAnchor(clock);
    }

    @PostMapping("/reset")
    public MarketClockInternalResponse reset(@Valid @RequestBody ResetClockRequest request,
                                              @RequestHeader("X-User-Id") UUID userId) {
        MarketClock clock = marketClockCommandService.reset(request.seq(), userId);
        return MarketClockInternalResponse.fromAnchor(clock);
    }
}
