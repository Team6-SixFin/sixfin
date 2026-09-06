package com.sparta.trading.presentation.controller.portfolio;

import com.sparta.trading.application.service.PortfolioQueryService;
import com.sparta.trading.presentation.dto.response.PortfolioResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/trading")
public class PortfolioQueryController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final PortfolioQueryService portfolioQueryService;

    // 총 자산 조회: 예수금과 보유 종목 평가금액을 합산한 포트폴리오 조회
    @GetMapping("/portfolio/me")
    public PortfolioResponse getMyPortfolio(@RequestHeader(USER_ID_HEADER) UUID userId) {
        return portfolioQueryService.getPortfolio(userId);
    }
}
