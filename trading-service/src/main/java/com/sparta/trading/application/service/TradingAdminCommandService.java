package com.sparta.trading.application.service;

import com.sparta.trading.domain.entity.Accounts;
import com.sparta.trading.domain.entity.CashLedgers;
import com.sparta.trading.domain.entity.Positions;
import com.sparta.trading.domain.repository.accounts.AccountsQueryRepository;
import com.sparta.trading.domain.repository.cashledgers.CashLedgersCommandRepository;
import com.sparta.trading.domain.repository.positions.PositionsQueryRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import com.sparta.trading.presentation.dto.request.TradingAdminResetAccountRequest;
import com.sparta.trading.presentation.dto.response.TradingAdminResetAccountResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TradingAdminCommandService {

    private final AccountsCommandRepository accountsCommandRepository;
    private final PositionsCommandRepository positionsCommandRepository;
    private static final ObjectMapper JSON_NODE_MAPPER = new ObjectMapper();

    private final AccountsQueryRepository tradingAccountsQueryRepository;
    private final PositionsQueryRepository positionRepository;
    private final CashLedgersCommandRepository cashLedgerRepository;
    private final TradingKafkaOutboxMarker outboxMarker;
    private final TradingKafkaOutboxPublisher outboxPublisher;

    /**
     * 대상 사용자의 계좌를 초기화한다.
     * OPEN 포지션은 손익 정산 없이 강제 종료하고, 원장은 삭제 대신 상계 행을 추가한다.
     */
    @Transactional
    public TradingAdminResetAccountResponse resetAccounts(
            UUID targetUserId,
            UUID adminUserId,
            TradingAdminResetAccountRequest request
    ) {
        Accounts account = accountsCommandRepository.findByUserIdForUpdate(targetUserId)
                .orElseThrow(() -> new CustomException(TradingErrorCode.ACCOUNT_NOT_FOUND));

        BigDecimal targetDeposit = request.initialDeposit() != null
                ? request.initialDeposit()
                : account.getInitialDeposit();

        List<Positions> openPositions = positionsCommandRepository
                .findAllOpenByAccountIdForUpdate(account.getId());

        boolean cashAlreadyAtTarget = account.getCashBalance().compareTo(targetDeposit) == 0;
        if (cashAlreadyAtTarget && openPositions.isEmpty()) {
            throw new CustomException(TradingErrorCode.NOTHING_TO_RESET);
        }

        Instant resetAt = Instant.now();
        for (Positions position : openPositions) {
            position.close(resetAt, adminUserId);
        }

        BigDecimal cashBalanceBefore = account.getCashBalance();
        account.reset(targetDeposit);
        BigDecimal adjustmentAmount = targetDeposit.subtract(cashBalanceBefore);

        CashLedgers ledger = cashLedgerRepository.save(
                CashLedgers.reset(account, adjustmentAmount, account.getCashBalance())
        );

        return TradingAdminResetAccountResponse.of(
                account,
                cashBalanceBefore,
                adjustmentAmount,
                openPositions.size(),
                ledger,
                resetAt
        );
    }

    /**
     * 관리자가 FAILED(또는 PENDING) Outbox 이벤트를 재발행한다.
     * payload가 오면 먼저 짧은 트랜잭션으로 덮어쓴 뒤, 카프카 전송은 트랜잭션 밖에서 이뤄지는
     * publishOne()에 위임한다.
     */
    public TradingAdminRetryOutBoxResponse retryOutBoxResponse(Long id, TradingAdminRetryOutBoxRequest request) {
        boolean payloadOverwritten = request.payload() != null;
        if (payloadOverwritten) {
            JsonNode newPayload = JSON_NODE_MAPPER.valueToTree(request.payload());
            outboxMarker.overwritePayload(id, newPayload);
        }

        OutboxPublishResult result = outboxPublisher.publishOne(id);

        return TradingAdminRetryOutBoxResponse.of(id, result, payloadOverwritten);
    }
}
