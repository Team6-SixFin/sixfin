package com.sparta.trading.presentation.controller.account;

import com.sparta.trading.application.service.AccountQueryService;
import com.sparta.trading.presentation.dto.response.AccountResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/trading")
public class AccountQueryController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final AccountQueryService accountQueryService;

    // 내 계좌 조회: 계좌가 없으면 초기 예수금 원장과 함께 생성한다.
    @GetMapping("/accounts/me")
    public AccountResponse getMyAccount(@RequestHeader(USER_ID_HEADER) UUID userId) {
        return accountQueryService.getOrCreateAccount(userId);
    }
}
