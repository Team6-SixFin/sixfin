package com.sparta.trading.presentation.controller.cashledger;

import com.sparta.trading.application.service.CashLedgerQueryService;
import com.sparta.trading.domain.entity.CashLedgerTxType;
import com.sparta.trading.global.response.PageResponse;
import com.sparta.trading.global.util.PageableUtil;
import com.sparta.trading.presentation.dto.response.CashLedgerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/trading/cash-ledgers")
public class CashLedgerQueryController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final CashLedgerQueryService cashLedgerQueryService;

    @GetMapping
    public PageResponse<CashLedgerResponse> getCashLedgers(
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @RequestParam(required = false) CashLedgerTxType txType,
            @PageableDefault(size = PageableUtil.DEFAULT_SIZE) Pageable pageable
    ) {
        return cashLedgerQueryService.getCashLedgers(userId, txType, pageable);
    }
}
