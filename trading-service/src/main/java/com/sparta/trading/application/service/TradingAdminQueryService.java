package com.sparta.trading.application.service;

import com.sparta.trading.application.dto.query.TradingAdminSearchOrderQuery;
import com.sparta.trading.application.dto.query.TradingSearchAccountsQuery;
import com.sparta.trading.application.dto.result.TradingAdminOrderQueryResult;
import com.sparta.trading.domain.entity.Accounts;
import com.sparta.trading.domain.entity.Orders;
import com.sparta.trading.domain.repository.accounts.TradingAccountsQueryRepository;
import com.sparta.trading.domain.repository.order.TradingOrderQueryRepository;
import com.sparta.trading.global.response.PageResponse;
import com.sparta.trading.presentation.dto.response.TradigAdminOrderResponseDto;
import com.sparta.trading.presentation.dto.response.TradingAccountsResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TradingAdminQueryService {

    private final TradingAccountsQueryRepository tradingAccountsQueryRepository;
    private final TradingOrderQueryRepository tradingOrderQueryRepository;

    public Page<TradingAccountsResponseDto> search(TradingSearchAccountsQuery tradingSearchAccountsQuery) {
        int page = tradingSearchAccountsQuery.page();
        int size = tradingSearchAccountsQuery.size();
        String sort = tradingSearchAccountsQuery.sort();

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, sort)
        );

        Page<Accounts> accounts = tradingAccountsQueryRepository.search(
                tradingSearchAccountsQuery.userId(),
        pageable);

        return accounts.map((TradingAccountsResponseDto::from));
    }

    public TradingAdminOrderQueryResult searchOrder(TradingAdminSearchOrderQuery tradingAdminSearchOrderQuery) {

        int page = tradingAdminSearchOrderQuery.page();
        int size = tradingAdminSearchOrderQuery.size();
        String sort = tradingAdminSearchOrderQuery.sort();

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, sort)
        );
        Page<Orders> orders = tradingOrderQueryRepository.searchOrder(pageable);

        List<Orders> orderList = orders.getContent();

        List<UUID> accountIds = orderList.stream()
                .map(Orders::getAccountId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();


        Map<UUID, UUID> accountUserMap = tradingAccountsQueryRepository.findAllById(accountIds).stream()
                .collect(Collectors.toMap(Accounts::getId, Accounts::getUserId));

        long filledCount = orderList.stream().filter(o -> "FILLED".equals(o.getStatus())).count();
        long rejectedCount = orderList.stream().filter(o -> "REJECTED".equals(o.getStatus())).count();

        Map<String, Long> summary = Map.of(
                "filledCount", filledCount,
                "rejectedCount", rejectedCount
        );

        Page<TradigAdminOrderResponseDto> responsePage = orders.map(order -> {
            String symbol = String.valueOf(order.getStockId()); // Stock 테이블 조회로 대체 권장
            UUID userId = accountUserMap.get(order.getAccountId());

            return new TradigAdminOrderResponseDto(
                    order.getId(),
                    order.getRequestId(),
                    userId,
                    order.getAccountId(),
                    order.getPositionId(),
                    symbol,
                    order.getSide(),
                    order.getOrderType(),
                    order.getQuantity(),
                    order.getStatus(),
                    order.getRejectReason(),
                    order.getPlannedStopLossPrice(),
                    order.getMarketTime(),
                    order.getCandleSeq(),
                    order.getCreatedAt()
            );
        });

        return new TradingAdminOrderQueryResult(summary, responsePage);
    }
}
