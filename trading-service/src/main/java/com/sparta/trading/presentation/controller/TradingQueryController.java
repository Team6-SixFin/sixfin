package com.sparta.trading.presentation.controller;

import com.sparta.trading.global.response.PageResponse;
import com.sparta.trading.global.util.PageableUtil;
import com.sparta.trading.presentation.dto.response.*;
import com.sparta.trading.application.service.TradingQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;


//search 같이 DB에 변동 사항이 없는 경우 사용하시면 됩니다.

@RestController
@RequestMapping("api/trading")
@RequiredArgsConstructor
public class TradingQueryController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final TradingQueryService tradingQueryService;

    // ==============================
    // = 시세
    // ==============================
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

    @GetMapping("/stocks")
    public PageResponse<TradingStockSearchResponse> searchStocks(
            @PageableDefault(size = PageableUtil.DEFAULT_SIZE) Pageable pageable
    ) {
        return tradingQueryService.searchStocks(pageable);
    }

    @GetMapping("/stocks/{symbol}/price")
    public TradingStockFindResponse findStocksBySymbol(
            @PathVariable("symbol") String symbol
    ) {
        return tradingQueryService.findStocksBySymbol(symbol);
    }

    @GetMapping("/stocks/{symbol}")
    public TradingStockDetailsFindResponse findStocksDetailsBySymbol(
            @PathVariable("symbol") String symbol
    ) {
        return tradingQueryService.findStocksDetailsBySymbol(symbol);
    }


    // ==============================
    // = 계좌 자산
    // ==============================

    // ==============================
    // = 매매
    // ==============================
    @GetMapping("/orders")
    public PageResponse<TradingOrderResponseDto> searchOrder(
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String side,
            @RequestParam(required = false) String symbol,
            @PageableDefault(size = PageableUtil.DEFAULT_SIZE) Pageable pageable
    ){
        return tradingQueryService.searchOrder(userId, status, side, symbol, pageable);
    }

    @GetMapping("/orders/{id}")
    public TradingOrderDetailResponseDto findOrderById(
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @PathVariable("id") UUID orderId
    ) {
        return tradingQueryService.findOrderById(userId, orderId);
    }

    @GetMapping("/executions")
    public PageResponse<TradingExecutionResponseDto> searchExecutions(
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @RequestParam(required = false) UUID positionId,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String side,
            @PageableDefault(size = PageableUtil.DEFAULT_SIZE) Pageable pageable
    ) {
        return tradingQueryService.searchExecutions(userId, positionId, symbol, side, pageable);
    }
}
