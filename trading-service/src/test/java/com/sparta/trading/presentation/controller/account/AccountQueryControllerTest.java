package com.sparta.trading.presentation.controller.account;

import com.sparta.trading.application.service.AccountQueryService;
import com.sparta.trading.global.exception.GlobalExceptionHandler;
import com.sparta.trading.presentation.dto.response.AccountResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AccountQueryControllerTest {

    @Mock
    private AccountQueryService accountQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AccountQueryController(accountQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getMyAccount_delegatesUsingUserIdHeader() throws Exception {
        UUID userId = UUID.randomUUID();
        when(accountQueryService.getOrCreateAccount(userId)).thenReturn(new AccountResponse(
                new BigDecimal("100000.0000"),
                new BigDecimal("100000.0000"),
                "USD",
                Instant.parse("2026-09-02T00:00:00Z")
        ));

        mockMvc.perform(get("/api/trading/accounts/me").header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"));

        verify(accountQueryService).getOrCreateAccount(userId);
    }

    @Test
    void getMyAccount_rejectsRequestWithoutUserIdHeader() throws Exception {
        mockMvc.perform(get("/api/trading/accounts/me"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMyAccount_rejectsInvalidUserIdHeader() throws Exception {
        mockMvc.perform(get("/api/trading/accounts/me")
                        .header("X-User-Id", "invalid-user-id"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMyAccount_hidesUnhandledExceptionMessage() throws Exception {
        UUID userId = UUID.randomUUID();
        when(accountQueryService.getOrCreateAccount(userId))
                .thenThrow(new IllegalStateException("sensitive internal detail"));

        mockMvc.perform(get("/api/trading/accounts/me")
                        .header("X-User-Id", userId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.details.reason")
                        .value("예기치 않은 서버 오류가 발생했습니다."));
    }
}
