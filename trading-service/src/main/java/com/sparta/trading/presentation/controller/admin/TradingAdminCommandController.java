package com.sparta.trading.presentation.controller.admin;

import com.sparta.trading.application.service.TradingAdminCommandService;
import com.sparta.trading.presentation.dto.request.TradingAdminResetAccountRequest;
import com.sparta.trading.presentation.dto.response.TradingAdminResetAccountResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** AdminAuthorizationInterceptor가 /api/trading/admin/** 전체에 ROLE_ADMIN을 요구한다. */
@RestController
@RequestMapping("api/trading/admin")
@RequiredArgsConstructor
public class TradingAdminCommandController {

    private final TradingAdminCommandService tradingAdminCommandService;

    @PostMapping("/accounts/{userId}/reset")
    public TradingAdminResetAccountResponse resetAccounts(
            @PathVariable UUID userId,
            @RequestHeader("X-User-Id") UUID adminUserId,
            @Valid @RequestBody TradingAdminResetAccountRequest request
    ) {
        return tradingAdminCommandService.resetAccounts(userId, adminUserId, request);
    }
}
