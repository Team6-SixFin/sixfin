package com.sparta.trading.application.service;

import com.sparta.trading.domain.entity.Accounts;
import com.sparta.trading.domain.entity.CashLedgerTxType;
import com.sparta.trading.domain.entity.CashLedgers;
import com.sparta.trading.domain.repository.accounts.AccountsCommandRepository;
import com.sparta.trading.domain.repository.accounts.AccountsQueryRepository;
import com.sparta.trading.domain.repository.cashledgers.CashLedgersCommandRepository;
import com.sparta.trading.domain.repository.cashledgers.CashLedgersQueryRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import com.sparta.trading.global.response.PageResponse;
import com.sparta.trading.global.util.PageableUtil;
import com.sparta.trading.presentation.dto.response.CashLedgerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CashLedgersQueryService {

    private final AccountsQueryRepository accountsQueryRepository;
    private final CashLedgersQueryRepository cashLedgersQueryRepository;

    @Transactional(readOnly = true)
    public PageResponse<CashLedgerResponse> getCashLedgers(
            UUID userId,
            CashLedgerTxType txType,
            Pageable pageable
    ) {
        // 사용자 계좌 조회
        Accounts account = accountsQueryRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(TradingErrorCode.ACCOUNT_NOT_FOUND));

        // 허용하지 않는 size는 20으로 정규화
        Pageable normalizedPageable = PageableUtil.normalize(pageable);

        // 계좌 ID와 txType 조건에 맞는 현금원장 조회
        Page<CashLedgers> cashLedgerPage = cashLedgersQueryRepository.findAllByAccountIdAndTxType(
                account.getId(),
                txType,
                normalizedPageable
        );

        // DTO로 변환
        Page<CashLedgerResponse> responsePage = cashLedgerPage.map(CashLedgerResponse::from);

        return PageResponse.of(responsePage);
    }
}
