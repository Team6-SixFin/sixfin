package com.sparta.trading.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.trading.domain.entity.Accounts;
import com.sparta.trading.domain.entity.CashLedgers;
import com.sparta.trading.domain.entity.Positions;
import com.sparta.trading.domain.repository.accounts.AccountsCommandRepository;
import com.sparta.trading.domain.repository.cashledgers.CashLedgersCommandRepository;
import com.sparta.trading.domain.repository.positions.PositionsCommandRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import com.sparta.trading.infrastructure.messaging.kafka.service.OutboxPublishResult;
import com.sparta.trading.infrastructure.messaging.kafka.service.TradingKafkaOutboxPublisher;
import com.sparta.trading.presentation.dto.request.TradingAdminResetAccountRequest;
import com.sparta.trading.presentation.dto.request.TradingAdminRetryOutBoxRequest;
import com.sparta.trading.presentation.dto.response.TradingAdminResetAccountResponse;
import com.sparta.trading.presentation.dto.response.TradingAdminRetryOutBoxResponse;
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

    private final CashLedgersCommandRepository cashLedgerRepository;
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

    /** FAILED 이벤트의 선점과 payload 교체는 한 트랜잭션으로, Kafka 전송은 그 밖에서 처리한다. */
    public TradingAdminRetryOutBoxResponse retryOutBoxResponse(Long id, TradingAdminRetryOutBoxRequest request) {
        boolean payloadOverwritten = request.payload() != null;
        JsonNode newPayload = payloadOverwritten ? JSON_NODE_MAPPER.valueToTree(request.payload()) : null;
        OutboxPublishResult result = outboxPublisher.republishOne(id, newPayload);

        return TradingAdminRetryOutBoxResponse.of(id, result, payloadOverwritten);
    }
}
