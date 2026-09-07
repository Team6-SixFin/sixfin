package com.sparta.trading.application.service;

import com.sparta.trading.application.dto.query.TradingAdminSearchExecutionQuery;
import com.sparta.trading.application.dto.query.TradingAdminSearchOrderQuery;
import com.sparta.trading.application.dto.query.TradingAdminSearchOutboxEventQurey;
import com.sparta.trading.application.dto.query.TradingReconciliationQuery;
import com.sparta.trading.application.dto.query.TradingSearchAccountsQuery;
import com.sparta.trading.application.dto.result.TradingAdminExecutionQueryResult;
import com.sparta.trading.application.dto.result.TradingAdminOrderQueryResult;
import com.sparta.trading.application.dto.result.TradingAdminOutboxEventQueryResult;
import com.sparta.trading.domain.entity.*;
import com.sparta.trading.domain.repository.accounts.TradingAccountsQueryRepository;
import com.sparta.trading.domain.repository.cashledger.CashLedgerRepository;
import com.sparta.trading.domain.repository.accounts.CashLedgersAccountsGroup;
import com.sparta.trading.domain.repository.cashledger.LedgerSequenceMismatchGroup;
import com.sparta.trading.domain.repository.execution.TradingExecutionQueryRepository;
import com.sparta.trading.domain.repository.order.DuplicateRequestGroup;
import com.sparta.trading.domain.repository.order.OrderRepository;
import com.sparta.trading.domain.repository.order.TradingOrderQueryRepository;
import com.sparta.trading.domain.repository.outboxEvent.TradingOutboxEventsQueryRepository;
import com.sparta.trading.domain.repository.position.DuplicateOpenPositionGroup;
import com.sparta.trading.domain.repository.position.PositionQuantityMismatchGroup;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
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
    private final OrderRepository orderRepository;
    private final CashLedgerRepository cashLedgerRepository;

    private final StringRedisTemplate redisTemplate;

    private static final int MAX_DETAIL_ITEMS = 100;


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

        //includePosition이 true이면 positions, stocks, Redis 순서대로 조회
        if (shouldIncludePositions) {
            List<Positions> positions = positionRepository.findAllByAccountIdAndStatus(account.getId(), "OPEN");

            if (!positions.isEmpty()) {
                valuationAt = Instant.now();

                // [N+1 해결 1] 모든 stockId 수집 후 stocks 테이블 1번만 배치 조회 (IN 쿼리)
                List<Long> stockIds = positions.stream().map(Positions::getStockId).toList();
                List<Stocks> stocksList = stocksRepository.findAllById(stockIds);

                // 빠른 조회를 위해 Map으로 변환 (Key: stockId, Value: Stocks)
                Map<Long, Stocks> stockMap = stocksList.stream()
                        .collect(Collectors.toMap(Stocks::getId, s -> s));

                // [N+1 해결 2] Redis 조회용 키 목록("price:AAPL", "price:NVDA") 일괄 생성
                List<String> redisKeys = positions.stream()
                        .map(p -> "price:" + stockMap.get(p.getStockId()).getSymbol())
                        .toList();

                // Redis 네트워크 요청 1번으로 일괄 조회 (multiGet)
                List<String> pricesFromRedis = redisTemplate.opsForValue().multiGet(redisKeys);

                List<TradingAdminAccountByUserResponseDto.PositionDto> dtos = new ArrayList<>();

                // 메모리 내 계산 및 DTO 변환 (추가 DB/Redis 조회 0건)
                for (int i = 0; i < positions.size(); i++) {
                    Positions p = positions.get(i);
                    Stocks stock = stockMap.get(p.getStockId());
                    String symbol = stock.getSymbol();

                    // Redis 값 매핑 (없으면 평균매수가로 fallback)
                    String priceStr = (pricesFromRedis != null) ? pricesFromRedis.get(i) : null;
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

        // 자산 계산 (총자산 = 예수금 + 평가금액)
        BigDecimal cashBalance = account.getCashBalance();
        BigDecimal orderableAmount = cashBalance;
        BigDecimal totalAsset = cashBalance.add(evaluationAmount);

        //응답 반환
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


    /** 원장·예수금 대조 등 정합성 검증을 일괄 실행한다. checks 미지정 시 전체 항목을 실행한다. */
    public TradingReconciliationResponse reconciliation(TradingReconciliationQuery query) {
        Instant checkedAt = Instant.now();
        long startNanos = System.nanoTime();

        List<ReconciliationCheckCode> requestedChecks = resolveCheckCodes(query.checks());
        boolean includeDetails = Boolean.TRUE.equals(query.includeDetails());
        UUID accountId = query.accountId();

        // account 404 메세지 전송
        if(accountId != null) {
            tradingAccountsQueryRepository.findById(accountId)
                    .orElseThrow(() -> new CustomException(TradingErrorCode.ACCOUNT_NOT_FOUND));
        }

        // 해당 하는 검사 하나씩 실행
        List<TradingReconciliationResponse.CheckResult> results = requestedChecks.stream()
                .map(checkCode -> runCheck(checkCode, accountId, includeDetails))
                .toList();

        ReconciliationStatus overallStatus = results.stream()
                .anyMatch(result -> result.status() == ReconciliationStatus.MISMATCH)
                ? ReconciliationStatus.MISMATCH
                : ReconciliationStatus.OK;

        int totalAccounts = accountId != null ? 1 : (int) tradingAccountsQueryRepository.count();
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        return new TradingReconciliationResponse(
                checkedAt,
                accountId != null ? accountId.toString() : "ALL",
                totalAccounts,
                overallStatus,
                elapsedMs,
                results
        );
    }

    // "check=NEGATIVE_CASH,NEGATIVE_QUANTITY,DUPLICATE_REQUEST,DUPLICATE_OPEN_POSITION&..."
    // 이런 형태로 쿼리 요청 와야함.
    private List<ReconciliationCheckCode> resolveCheckCodes(String checksParam) {
        if (checksParam == null || checksParam.isBlank()) {
            return List.of(ReconciliationCheckCode.values());
        }
        return Arrays.stream(checksParam.split(","))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .map(this::parseCheckCode)
                .toList();
    }

    private ReconciliationCheckCode parseCheckCode(String rawCode) {
        try {
            return ReconciliationCheckCode.valueOf(rawCode);
        } catch (IllegalArgumentException e) {
            throw new CustomException(TradingErrorCode.INVALID_CHECK_CODE, "지원하지 않는 검사 항목입니다: " + rawCode);
        }
    }

    // 각 테스트는 대체로 JPQL 조인 쿼리 1번으로 감사확인
    private TradingReconciliationResponse.CheckResult runCheck(
            ReconciliationCheckCode checkCode, UUID accountId, boolean includeDetails
    ) {
        return switch (checkCode) {
            case NEGATIVE_CASH -> checkNegativeCash(accountId, includeDetails);
            case NEGATIVE_QUANTITY -> checkNegativeQuantity(accountId, includeDetails);
            case DUPLICATE_REQUEST -> checkDuplicateRequest(accountId, includeDetails);
            case DUPLICATE_OPEN_POSITION -> checkDuplicateOpenPosition(accountId, includeDetails);
            case OUTBOX_PENDING -> checkOutboxPending(includeDetails);
            case LEDGER_BALANCE -> checkLedgerBalance(accountId, includeDetails);
            case POSITION_QUANTITY -> checkPositionQuantity(accountId, includeDetails);
            case LEDGER_SEQUENCE -> checkLedgerSequence(accountId, includeDetails);
        };
    }

    // ===== 검사 항목 =====
    /**
     * includeDetails 공용 처리 로직. 8개 검사 항목이 전부 이 규칙을 공유하므로 한 곳에만 둔다.
     * includeDetails=false면 상세 조회 쿼리 자체를 실행하지 않는다(count만으로 충분하므로).
     * true일 때 실행되는 조회는 호출부에서 Pageable로 최대 100건을 이미 제한해서 넘겨야 한다.
     */
    private <T> List<Map<String, Object>> buildDetails(
            boolean includeDetails, Supplier<List<T>> detailFetcher, Function<T, Map<String, Object>> toDetail
    ) {
        if (!includeDetails) {
            return List.of();
        }
        // Pageable로 이미 DB에서 잘라오는 조회든, 그렇지 않은 조회든 여기서 한 번 더 방어적으로 캡을 건다.
        return detailFetcher.get().stream().limit(MAX_DETAIL_ITEMS).map(toDetail).toList();
    }

    /** accounts.cash_balance < 0 인 계좌. accountId가 null이면 전체 계좌 대상. */
    private TradingReconciliationResponse.CheckResult checkNegativeCash(UUID accountId, boolean includeDetails) {
        long mismatchedCount = tradingAccountsQueryRepository.countNegativeCashBalance(accountId);

        Supplier<List<Accounts>> negativeAccountsFinder = () -> tradingAccountsQueryRepository.findNegativeCashBalance(accountId, PageRequest.of(0, MAX_DETAIL_ITEMS));

        List<Map<String, Object>> details = buildDetails(
                includeDetails,
                negativeAccountsFinder,
                account -> Map.of(
                        "accountId", account.getId(),
                        "userId", account.getUserId(),
                        "cashBalance", account.getCashBalance()
                )
        );

        ReconciliationStatus status = mismatchedCount == 0 ? ReconciliationStatus.OK : ReconciliationStatus.MISMATCH;
        return new TradingReconciliationResponse.CheckResult(
                ReconciliationCheckCode.NEGATIVE_CASH,
                ReconciliationCheckCode.NEGATIVE_CASH.getDescription(),
                status,
                (int) mismatchedCount,
                details
        );
    }


    /** positions.quantity < 0 인 포지션 */
    private TradingReconciliationResponse.CheckResult checkNegativeQuantity(UUID accountId, boolean includeDetails) {
        long mismatchedCount = positionRepository.countNegativeQuantityByAccountId(accountId);

        List<Map<String, Object>> details = buildDetails(
                includeDetails,
                () -> positionRepository.findNegativeQuantityByAccountId(accountId, PageRequest.of(0, MAX_DETAIL_ITEMS)),
                position -> Map.of(
                        "positionId", position.getId(),
                        "accountId", position.getAccountId(),
                        "userId", position.getUserId(),
                        "quantity", position.getQuantity()
                )
        );

        ReconciliationStatus status = mismatchedCount == 0 ? ReconciliationStatus.OK : ReconciliationStatus.MISMATCH;
        return new TradingReconciliationResponse.CheckResult(
                ReconciliationCheckCode.NEGATIVE_QUANTITY,
                ReconciliationCheckCode.NEGATIVE_QUANTITY.getDescription(),
                status,
                (int) mismatchedCount,
                details
        );
    }

    /** 동일 orders.request_id 가 2건 이상 */
    private TradingReconciliationResponse.CheckResult checkDuplicateRequest(UUID accountId, boolean includeDetails) {
        List<DuplicateRequestGroup> duplicateGroups = orderRepository.findDuplicateRequestGroups(accountId);

        List<Map<String, Object>> details = buildDetails(includeDetails,
                () -> duplicateGroups,
                group -> Map.of(
                "requestId", group.getRequestId(),
                "duplicateCount", group.getDuplicateCount()
        ));

        ReconciliationStatus status = duplicateGroups.isEmpty() ? ReconciliationStatus.OK : ReconciliationStatus.MISMATCH;
        return new TradingReconciliationResponse.CheckResult(
                ReconciliationCheckCode.DUPLICATE_REQUEST,
                ReconciliationCheckCode.DUPLICATE_REQUEST.getDescription(),
                status,
                duplicateGroups.size(),
                details
        );
    }

    /**
     * 동일 계좌·종목에 OPEN 포지션이 2건 이상.
     */
    private TradingReconciliationResponse.CheckResult checkDuplicateOpenPosition(UUID accountId, boolean includeDetails) {
        List<DuplicateOpenPositionGroup> duplicateGroups = positionRepository.findDuplicateOpenPositionGroups(accountId);

        List<Map<String, Object>> details = buildDetails(includeDetails,
                () -> duplicateGroups,
                group -> Map.of(
                "accountId", group.getAccountId(),
                "stockId", group.getStockId(),
                "duplicateCount", group.getDuplicateCount()
        ));

        ReconciliationStatus status = duplicateGroups.isEmpty() ? ReconciliationStatus.OK : ReconciliationStatus.MISMATCH;
        return new TradingReconciliationResponse.CheckResult(
                ReconciliationCheckCode.DUPLICATE_OPEN_POSITION,
                ReconciliationCheckCode.DUPLICATE_OPEN_POSITION.getDescription(),
                status,
                duplicateGroups.size(),
                details
        );
    }

    /** 미발행(published가 아닌) Outbox 적체. 임계치는 별도로 두지 않고 0건 초과면 바로 MISMATCH로 판정한다. */
    private TradingReconciliationResponse.CheckResult checkOutboxPending(boolean includeDetails) {
        long pendingCount = tradingOutboxEventsQueryRepository.countUnpublished();

        Supplier<List<OutboxEvents>> unpublishedOutboxFinder =
                () -> tradingOutboxEventsQueryRepository.findUnpublished(PageRequest.of(0, MAX_DETAIL_ITEMS));

        List<Map<String, Object>> details = buildDetails(
                includeDetails,
                unpublishedOutboxFinder,
                event -> Map.of(
                        "outboxId", event.getId(),
                        "eventType", event.getEventType(),
                        "status", event.getStatus(),
                        "retryCount", event.getRetryCount()
                )
        );

        ReconciliationStatus status = pendingCount > 0 ? ReconciliationStatus.MISMATCH : ReconciliationStatus.OK;
        return new TradingReconciliationResponse.CheckResult(
                ReconciliationCheckCode.OUTBOX_PENDING,
                ReconciliationCheckCode.OUTBOX_PENDING.getDescription(),
                status,
                (int) pendingCount,
                details
        );
    }

    /** SUM(cash_ledgers.amount) = accounts.cash_balance */
    private TradingReconciliationResponse.CheckResult checkLedgerBalance(UUID accountId, boolean includeDetails) {
        List<CashLedgersAccountsGroup> mismatched =
                tradingAccountsQueryRepository.findLedgerBalanceMismatches(accountId);

        List<Map<String, Object>> details = buildDetails(includeDetails, () -> mismatched, group -> Map.of(
                "accountId", group.getAccountId(),
                "userId", group.getUserId(),
                "cashBalance", group.getCashBalance(),
                "ledgerSum", group.getLedgerSum(),
                "diff", group.getCashBalance().subtract(group.getLedgerSum())
        ));

        ReconciliationStatus status = mismatched.isEmpty() ? ReconciliationStatus.OK : ReconciliationStatus.MISMATCH;
        return new TradingReconciliationResponse.CheckResult(
                ReconciliationCheckCode.LEDGER_BALANCE,
                ReconciliationCheckCode.LEDGER_BALANCE.getDescription(),
                status,
                mismatched.size(),
                details
        );
    }

    /** positions.quantity = 매수 체결 합계 − 매도 체결 합계 */
    private TradingReconciliationResponse.CheckResult checkPositionQuantity(UUID accountId, boolean includeDetails) {
        List<PositionQuantityMismatchGroup> mismatched = positionRepository.findPositionQuantityMismatches(accountId);

        List<Map<String, Object>> details = buildDetails(includeDetails,
                () -> mismatched,
                group -> Map.of(
                        "accountId", group.getAccountId(),
                        "positionId", group.getPositionId(),
                        "positionQuantity", group.getPositionQuantity(),
                        "executionNetQuantity", group.getExecutionNetQuantity()
                ));

        ReconciliationStatus status = mismatched.isEmpty() ? ReconciliationStatus.OK : ReconciliationStatus.MISMATCH;
        return new TradingReconciliationResponse.CheckResult(
                ReconciliationCheckCode.POSITION_QUANTITY,
                ReconciliationCheckCode.POSITION_QUANTITY.getDescription(),
                status,
                mismatched.size(),
                details
        );
    }

    /** 직전 balance_after + amount = 현재 balance_after */
    private TradingReconciliationResponse.CheckResult checkLedgerSequence(UUID accountId, boolean includeDetails) {

        List<LedgerSequenceMismatchGroup> mismatchGroupList = cashLedgerRepository.findLedgerSequenceMismatches(accountId);

        List<Map<String, Object>> details = buildDetails(includeDetails,
                () -> mismatchGroupList,
                group -> Map.of(
                        "accountId", group.getAccountId(),
                        "ledgerId", group.getLedgerId(),
                        "amount", group.getAmount(),
                        "balanceAfter", group.getBalanceAfter(),
                        "previousBalanceAfter", group.getPrevBalanceAfter()
                ));

        ReconciliationStatus status = mismatchGroupList.isEmpty() ? ReconciliationStatus.OK : ReconciliationStatus.MISMATCH;
        return new TradingReconciliationResponse.CheckResult(
                ReconciliationCheckCode.LEDGER_SEQUENCE,
                ReconciliationCheckCode.LEDGER_SEQUENCE.getDescription(),
                status,
                mismatchGroupList.size(),
                details
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
