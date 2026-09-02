package com.sparta.trading.presentation.controller;

import com.sparta.trading.application.dto.query.MarketClockResponse;
import com.sparta.trading.application.service.TradingQueryService;
import com.sparta.trading.domain.entity.MarketClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


//search 같이 DB에 변동 사항이 없는 경우 사용하시면 됩니다.

@RestController
@RequestMapping("api/trading")
@RequiredArgsConstructor
public class TradingQueryController {

    private final TradingQueryService tradingQueryService;

/**
 * 작성자 : 김준서
 * 최초 작성일 : 08/31
 * 최종 수정일 : 08/31
 * 기능 :
 * 설명 :
 * @Param:
 **/
    @GetMapping("/market/clock")
    public MarketClockResponse getClock() {
        return tradingQueryService.getClock();
    }

}
