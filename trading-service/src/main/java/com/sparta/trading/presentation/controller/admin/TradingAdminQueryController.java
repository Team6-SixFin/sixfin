package com.sparta.trading.presentation.controller.admin;

import com.sparta.trading.application.dto.query.TradingAdminSearchExecutionQuery;
import com.sparta.trading.application.dto.query.TradingAdminSearchOrderQuery;
import com.sparta.trading.application.dto.query.TradingSearchAccountsQuery;
import com.sparta.trading.application.dto.result.TradingAdminExecutionQueryResult;
import com.sparta.trading.application.dto.result.TradingAdminOrderQueryResult;
import com.sparta.trading.application.service.TradingAdminQueryService;
import com.sparta.trading.domain.entity.Accounts;
import com.sparta.trading.global.response.PageResponse;
import com.sparta.trading.presentation.dto.response.TradigAdminOrderResponseDto;
import com.sparta.trading.presentation.dto.response.TradingAccountsResponseDto;
import com.sparta.trading.presentation.dto.response.TradingAdminExecutionResponseDto;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;


//search 같이 DB에 변동 사항이 없는 경우 사용하시면 됩니다.

@RestController
@RequestMapping("api/trading/admin")
@RequiredArgsConstructor
public class TradingAdminQueryController {

    private final TradingAdminQueryService tradingAdminQueryService;

    /**
     * 작성자 : 정승호
     * 최초 작성일 : 26-08-31
     * 최종 수정일 : 26-09-01
     * 기능 : 어드민 권한을 가진 계정이 유저 전체의 계좌를 조회
     * 설명 :
     * @Param: userId, sort, page, size
     **/
    @GetMapping("/accounts")
    public PageResponse<TradingAccountsResponseDto> searchAll(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
            ){
        Page<TradingAccountsResponseDto> accountPage = tradingAdminQueryService.search(new TradingSearchAccountsQuery(
                userId,
                sort,
                page,
                size));

        return PageResponse.of(accountPage);
    }

    /**
     * 작성자 : 정승호
     * 최초 작성일 : 26-09-01
     * 최종 수정일 :
     * 기능 :
     * 설명 :
     * @Param:
     **/
    @GetMapping("/orders")
    public PageResponse<TradigAdminOrderResponseDto> searchOrder(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String side,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    )
    {
        TradingAdminOrderQueryResult result = tradingAdminQueryService.searchOrder(
                new TradingAdminSearchOrderQuery(userId, symbol, side, status, from, to, sort, page, size));
        return PageResponse.of(result.summary(),result.page());
    }


    /**
     * 작성자 :
     * 최초 작성일 :26-09-02
     * 최종 수정일 :
     * 기능 :
     * 설명 :
     * @Param:
     **/
    @GetMapping("/executions")
    public PageResponse<TradingAdminExecutionResponseDto> searchExecutions(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID positionId,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String side,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ){
        TradingAdminExecutionQueryResult result = tradingAdminQueryService.searchExecuation(
             new TradingAdminSearchExecutionQuery(userId, positionId, symbol, side,from,to,sort,page,size));
        return PageResponse.of(result.summary(),result.page());
    }
}
