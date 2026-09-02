package com.sparta.trading.presentation.controller.admin;

import com.sparta.trading.application.dto.query.TradingAdminSearchOrderQuery;
import com.sparta.trading.application.dto.query.TradingSearchAccountsQuery;
import com.sparta.trading.application.service.TradingAdminQueryService;
import com.sparta.trading.domain.entity.Accounts;
import com.sparta.trading.global.response.PageResponse;
import com.sparta.trading.presentation.dto.response.TradigAdminOrderResponseDto;
import com.sparta.trading.presentation.dto.response.TradingAccountsResponseDto;
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
     * 작성자 :
     * 최초 작성일 :
     * 최종 수정일 :
     * 기능 :
     * 설명 :
     * @Param:
     **/
    /*ToDO
    * 유저 권한 기능이 생기면 인가 기능 추가할것
    * */
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
        Page<TradigAdminOrderResponseDto> orderResponseDto = tradingAdminQueryService.searchOrder(
                new TradingAdminSearchOrderQuery(userId, symbol, side, status, from, to, sort, page, size));
        return PageResponse.of(orderResponseDto);
    }

}
