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
import com.sparta.trading.domain.repository.position.PositionRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.GlobalErrorCode;
import com.sparta.trading.global.exception.TradingErrorCode;
import com.sparta.trading.global.util.PageableUtil;
import com.sparta.trading.infrastructure.persistence.repository.stocks.StocksRepository;
import com.sparta.trading.presentation.dto.response.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
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
    private final PositionRepository positionRepository;
    private final StocksRepository stocksRepository;
    private final StringRedisTemplate redisTemplate;


    public Page<TradingAccountsResponseDto> search(TradingSearchAccountsQuery tradingSearchAccountsQuery) {
        Pageable pageable = PageableUtil.createDescPageable(
                tradingSearchAccountsQuery.page(),
                tradingSearchAccountsQuery.size(),
                tradingSearchAccountsQuery.sort()
        );

        Page<Accounts> accounts = tradingAccountsQueryRepository.search(
                tradingSearchAccountsQuery.userId(),
        pageable);

        return accounts.map((TradingAccountsResponseDto::from));
    }

    public TradingAdminOrderQueryResult searchOrder(TradingAdminSearchOrderQuery tradingAdminSearchOrderQuery) {

        Pageable pageable = PageableUtil.createDescPageable(
                tradingAdminSearchOrderQuery.page(),
                tradingAdminSearchOrderQuery.size(),
                tradingAdminSearchOrderQuery.sort()
        );

        //검색 조건 처리
        validateDateRange(tradingAdminSearchOrderQuery.from(),tradingAdminSearchOrderQuery.to());

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

        Pageable pageable = PageableUtil.createDescPageable(
                tradingExecutionQuery.page(),
                tradingExecutionQuery.size(),
                tradingExecutionQuery.sort()
        );

        //검색 조건 처리
        validateDateRange(tradingExecutionQuery.from(),tradingExecutionQuery.to());

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
                    execution.getAvgEntryPriceAtExecution(),
                    execution.getRealizedProfit(),
                    execution.getCandleSeq(),
                    execution.getMarketTime(),
                    execution.getCreatedAt()
            );
        });

        return new TradingAdminExecutionQueryResult(summary,responsePage);
    }

    public TradingAdminOutboxEventQueryResult searchOutbox(TradingAdminSearchOutboxEventQurey tradingAdminSearchOutboxEventQurey){

        Pageable pageable = PageableUtil.createDescPageable(
                tradingAdminSearchOutboxEventQurey.page(),
                tradingAdminSearchOutboxEventQurey.size(),
                tradingAdminSearchOutboxEventQurey.sort()
        );

        //검색 조건 처리
        validateDateRange(tradingAdminSearchOutboxEventQurey.from(),tradingAdminSearchOutboxEventQurey.to());

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
                .map(OutboxEvents::getOccurredAt)
                .min(Comparator.naturalOrder())
                .orElse(null);

        Map<String, Object> summary = new HashMap<>();
        summary.put("pendingCount", pendingCount);
        summary.put("failedCount", failedCount);
        summary.put("oldestPendingAt", oldestPendingAt);

        boolean includePayload = Boolean.TRUE.equals(tradingAdminSearchOutboxEventQurey.includePayload());


        Page<TradingAdminOutboxEventResponseDto> responseDtoPage = outboxEvents.map(outboxEvent ->{
            Object payloadValue = includePayload ? outboxEvent.getPayload() : null;
            Long delayedSeconds = calculateDelayedSeconds(outboxEvent);
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
                   payloadValue,
                   outboxEvent.getOccurredAt(),
                   outboxEvent.getPublishedAt(),
                   delayedSeconds
           );
        });

        return new TradingAdminOutboxEventQueryResult(summary,responseDtoPage);
    }


    public TradingAdminAccountByUserResponseDto searchAccountByUser(UUID userId, Boolean includePosition) {

        Accounts account = tradingAccountsQueryRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(GlobalErrorCode.USER_NOT_FOUND, "계좌를 찾을 수 없습니다."));

        boolean shouldIncludePositions = Boolean.TRUE.equals(includePosition);

        List<TradingAdminAccountByUserResponseDto.PositionDto> positionDtos = Collections.emptyList();
        BigDecimal evaluationAmount = BigDecimal.ZERO;
        Instant valuationAt = null;

        // 2. includePosition이 true이면 positions, stocks, Redis 순서대로 조회
        if (shouldIncludePositions) {
            List<Positions> positions = positionRepository.findAllByAccountIdAndStatus(account.getId(), "OPEN");

            if (!positions.isEmpty()) {
                valuationAt = Instant.now();
                List<TradingAdminAccountByUserResponseDto.PositionDto> dtos = new ArrayList<>();

                for (Positions p : positions) {
                    // stocks 테이블 조회 (symbol 가져오기)
                    Stocks stock = stocksRepository.findById(p.getStockId())
                            .orElseThrow(() -> new CustomException(TradingErrorCode.STOCK_NOT_FOUND, "주식 정보를 찾을 수 없습니다."));

                    String symbol = stock.getSymbol();

                    // Redis에서 price:{symbol} 조회
                    String priceStr = redisTemplate.opsForValue().get("price:" + symbol);

                    // Redis 가격이 없으면 평균매수가로 fallback
                    BigDecimal currentPrice = (priceStr != null)
                            ? new BigDecimal(priceStr)
                            : p.getAverageEntryPrice();

                    // 평가손익 = (현재가 - 평균매수가) * 수량
                    BigDecimal unrealizedProfit = currentPrice.subtract(p.getAverageEntryPrice())
                            .multiply(BigDecimal.valueOf(p.getQuantity()));

                    // 종목 평가금액 합산
                    BigDecimal posEvalAmount = currentPrice.multiply(BigDecimal.valueOf(p.getQuantity()));
                    evaluationAmount = evaluationAmount.add(posEvalAmount);

                    dtos.add(new TradingAdminAccountByUserResponseDto.PositionDto(
                            p.getId(),
                            symbol,
                            p.getQuantity(),
                            p.getAverageEntryPrice(),
                            currentPrice,
                            unrealizedProfit
                    ));
                }
                positionDtos = dtos;
            }
        }

        // 3. 자산 계산 (총자산 = 예수금 + 평가금액)
        BigDecimal cashBalance = account.getCashBalance();
        BigDecimal orderableAmount = cashBalance; // MVP 기준 동일
        BigDecimal totalAsset = cashBalance.add(evaluationAmount);

        // 4. 응답 반환
        return new TradingAdminAccountByUserResponseDto(
                account.getId(),
                account.getUserId(),
                cashBalance,
                orderableAmount,
                account.getInitialDeposit(),
                evaluationAmount,
                totalAsset,
                valuationAt,
                positionDtos,
                account.getCreatedAt()
        );
    }



    //날짜 검증
    public static void validateDateRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new CustomException(GlobalErrorCode.INVALID_REQUEST, "from이 to보다 이후일 수 없습니다.");
        }
    }

    //아웃박스 - delayedSecond 계산
    private Long calculateDelayedSeconds(OutboxEvents outboxEvent) {
        if (outboxEvent.getOccurredAt() == null) {
            return 0L;
        }

        Instant now = Instant.now();

        // PUBLISHED인 경우: publishedAt - occurredAt
        if (OutboxStatus.PUBLISHED.equals(outboxEvent.getStatus())) {
            if (outboxEvent.getPublishedAt() == null) {
                return 0L;
            }
            return Duration.between(outboxEvent.getOccurredAt(), outboxEvent.getPublishedAt()).getSeconds();
        }

        // PENDING, FAILED인 경우: 현재 시간(now) - occurredAt
        return Duration.between(outboxEvent.getOccurredAt(), now).getSeconds();
    }

}
