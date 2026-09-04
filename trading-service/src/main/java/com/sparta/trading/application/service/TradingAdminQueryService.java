package com.sparta.trading.application.service;

import com.sparta.trading.application.dto.query.TradingAdminSearchExecutionQuery;
import com.sparta.trading.application.dto.query.TradingAdminSearchOrderQuery;
import com.sparta.trading.application.dto.query.TradingAdminSearchOutboxEventQurey;
import com.sparta.trading.application.dto.query.TradingSearchAccountsQuery;
import com.sparta.trading.application.dto.result.TradingAdminExecutionQueryResult;
import com.sparta.trading.application.dto.result.TradingAdminOrderQueryResult;
import com.sparta.trading.application.dto.result.TradingAdminOutboxEventQueryResult;
import com.sparta.trading.domain.entity.*;
import com.sparta.trading.domain.repository.accounts.TradingAccountsQueryRepository;
import com.sparta.trading.domain.repository.execution.TradingExecutionQueryRepository;
import com.sparta.trading.domain.repository.order.TradingOrderQueryRepository;
import com.sparta.trading.domain.repository.outboxEvent.TradingOutboxEventsQueryRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.GlobalErrorCode;
import com.sparta.trading.infrastructure.persistence.repository.stocks.StocksRepository;
import com.sparta.trading.presentation.dto.response.TradigAdminOrderResponseDto;
import com.sparta.trading.presentation.dto.response.TradingAccountsResponseDto;
import com.sparta.trading.presentation.dto.response.TradingAdminExecutionResponseDto;
import com.sparta.trading.presentation.dto.response.TradingAdminOutboxEventResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TradingAdminQueryService {

    private final TradingAccountsQueryRepository tradingAccountsQueryRepository;
    private final TradingOrderQueryRepository tradingOrderQueryRepository;
    private final TradingExecutionQueryRepository tradingExecutionQueryRepository;
    private final TradingOutboxEventsQueryRepository tradingOutboxEventsQueryRepository;
    private final StocksRepository stocksRepository;

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

        //검색 조건 처리
        if (tradingAdminSearchOrderQuery.from() != null && tradingAdminSearchOrderQuery.to() != null) {
            if (tradingAdminSearchOrderQuery.from().isAfter(tradingAdminSearchOrderQuery.to())) {
                throw new CustomException(GlobalErrorCode.INVALID_REQUEST,"from이 to보다 이후일 수 없습니다.");
            }
        }


        //심벌
        Long targetStockId = null;
        if(tradingAdminSearchOrderQuery.symbol() != null){
            targetStockId = stocksRepository.findBySymbol(tradingAdminSearchOrderQuery.symbol())
                    .map(Stocks::getId)
                    .orElse(-1L);   //검색 조건에 없으면 -1을 주어 검색이 되지 않도록 처리
        }

        // 유저 Id
        List<UUID> targetAccountIds = null;
        if (tradingAdminSearchOrderQuery.userId() != null) {
            List<Accounts> userAccounts = tradingAccountsQueryRepository.findAllByUserId(tradingAdminSearchOrderQuery.userId());
            if (userAccounts.isEmpty()) {
                targetAccountIds = List.of(UUID.randomUUID()); // 해당 유저의 계좌가 없으면 결과 없음 처리
            } else {
                targetAccountIds = userAccounts.stream().map(Accounts::getId).toList();
            }
        }


        Page<Orders> orders = tradingOrderQueryRepository.searchOrder(
                tradingAdminSearchOrderQuery,
                targetStockId,
                targetAccountIds,
                pageable);
        List<Orders> orderList = orders.getContent();

        //유저 아이디와 심벌 가져오기
        List<Long> stockIds = orderList.stream()
                .map(Orders::getStockId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<UUID> accountIds = orderList.stream()
                .map(Orders::getAccountId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, String> stockSymbolMap = stocksRepository.findAllById(stockIds).stream()
                .collect(Collectors.toMap(Stocks::getId,Stocks::getSymbol));

        Map<UUID, UUID> accountUserMap = tradingAccountsQueryRepository.findAllById(accountIds).stream()
                .collect(Collectors.toMap(Accounts::getId, Accounts::getUserId));

        //summary 계산
        long filledCount = orderList.stream().filter(o -> "FILLED".equals(o.getStatus())).count();
        long rejectedCount = orderList.stream().filter(o -> "REJECTED".equals(o.getStatus())).count();

        Map<String, Long> summary = Map.of(
                "filledCount", filledCount,
                "rejectedCount", rejectedCount
        );

        Page<TradigAdminOrderResponseDto> responsePage = orders.map(order -> {
            String symbol = stockSymbolMap.get(order.getStockId());
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

    //체결 전체 조회
    public TradingAdminExecutionQueryResult searchExecuation(TradingAdminSearchExecutionQuery tradingExecutionQuery) {

        int page = tradingExecutionQuery.page();
        int size = tradingExecutionQuery.size();
        String sort = tradingExecutionQuery.sort();

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, sort)
        );

        //검색 조건 처리
        if (tradingExecutionQuery.from() != null && tradingExecutionQuery.to() != null) {
            if (tradingExecutionQuery.from().isAfter(tradingExecutionQuery.to())) {
                throw new CustomException(GlobalErrorCode.INVALID_REQUEST,"from이 to보다 이후일 수 없습니다.");
            }
        }

        //심벌
        Long targetStockId = null;
        if(tradingExecutionQuery.symbol() != null){
            targetStockId = stocksRepository.findBySymbol(tradingExecutionQuery.symbol())
                    .map(Stocks::getId)
                    .orElse(-1L);   //검색 조건에 없으면 -1을 주어 검색이 되지 않도록 처리
        }

        //조회
        Page<Executions> executions = tradingExecutionQueryRepository.searchExecution(
                tradingExecutionQuery,
                targetStockId,
                pageable
        );
        List<Executions> list = executions.getContent();

        //심벌 가져오기
        List<Long> stockIds = list.stream()
                .map(Executions::getStockId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, String> stockSymbolMap = stocksRepository.findAllById(stockIds).stream()
                .collect(Collectors.toMap(Stocks::getId,Stocks::getSymbol));

        //summary 계산
        BigDecimal totalAmount = list.stream()
                .map(Executions::getExecutedAmount)
                .reduce(BigDecimal.ZERO,BigDecimal::add);

        BigDecimal totalRealizedProfit = list.stream()
                .map(Executions::getRealizedProfit)
                .reduce(BigDecimal.ZERO,BigDecimal::add);

        Map<String, BigDecimal> summary = Map.of(
                "totalAmoumnt" ,totalAmount,
                "totalRealizedProfit",totalRealizedProfit
        );

        Page<TradingAdminExecutionResponseDto> responsePage = executions.map(execution ->{
            String symbol = stockSymbolMap.get(execution.getStockId());

            return new TradingAdminExecutionResponseDto(
                    execution.getId(),
                    execution.getOrderId(),
                    execution.getPositionId(),
                    execution.getUserId(),
                    symbol,
                    execution.getSide(),
                    execution.getExecutedPrice(),
                    execution.getExecutedQuantity(),
                    execution.getExecutedAmount(),
                    execution.getAvgEntyPriceAtExecuation(),
                    execution.getRealizedProfit(),
                    execution.getCandleSeq(),
                    execution.getMarketTime(),
                    execution.getCreatedAt()
            );
        });

        return new TradingAdminExecutionQueryResult(summary,responsePage);
    }

    public TradingAdminOutboxEventQueryResult searchOutbox(TradingAdminSearchOutboxEventQurey tradingAdminSearchOutboxEventQurey){

        int page = tradingAdminSearchOutboxEventQurey.page();
        int size = tradingAdminSearchOutboxEventQurey.size();
        String sort = tradingAdminSearchOutboxEventQurey.sort();

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, sort)
        );

        //todo - from to 설정은 공통을 묶어서 함수로 처리해도 될것 같음
        //검색 조건 처리
        if (tradingAdminSearchOutboxEventQurey.from() != null && tradingAdminSearchOutboxEventQurey.to() != null) {
            if (tradingAdminSearchOutboxEventQurey.from().isAfter(tradingAdminSearchOutboxEventQurey.to())) {
                throw new CustomException(GlobalErrorCode.INVALID_REQUEST,"from이 to보다 이후일 수 없습니다.");
            }
        }

        //조회
        Page<OutboxEvents> outboxEvents = tradingOutboxEventsQueryRepository.searchOutbox(
                tradingAdminSearchOutboxEventQurey,
                pageable
        );
        List<OutboxEvents> outboxEventsList = outboxEvents.getContent();

        //summary 계산
        long pendingCount = outboxEventsList.stream().filter(o -> "PENDING".equals(o.getStatus())).count();
        long failedCount = outboxEventsList.stream().filter(o -> "FAILED".equals(o.getStatus())).count();

        Instant oldestPendingAt = outboxEventsList.stream()
                .filter(o -> "PENDING".equals(o.getStatus()))
                .map(OutboxEvents::getCreatedAt)
                .min(Comparator.naturalOrder())
                .orElse(null);

        Map<String, Object> summary = new HashMap<>();
        summary.put("pendingCount", pendingCount);
        summary.put("failedCount", failedCount);
        summary.put("oldestPendingAt", oldestPendingAt);

        Page<TradingAdminOutboxEventResponseDto> responseDtoPage = outboxEvents.map(outboxEvent ->{
           return new TradingAdminOutboxEventResponseDto(
                   outboxEvent.getId(),
                   outboxEvent.getEventId(),
                   outboxEvent.getEventType(),
                   outboxEvent.getEventVersion(),
                   outboxEvent.getAggregateType(),
                   outboxEvent.getAggregateId(),
                   outboxEvent.getPartitionKey(),
                   outboxEvent.getStatus(),
                   outboxEvent.getRetryCount(),
                   outboxEvent.getLastError(),
                   outboxEvent.getPayload(),
                   outboxEvent.getOccurredAt(),
                   outboxEvent.getPublishedAt(),
                   0L                   //어디서 가져오는지 전혀 모름
           );
        });

        return new TradingAdminOutboxEventQueryResult(summary,responseDtoPage);
    }
}
