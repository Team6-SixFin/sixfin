package com.sparta.trading.presentation.controller.account;

import com.sparta.trading.application.service.AccountQueryService;
import com.sparta.trading.application.service.PortfolioQueryService;
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
class AssetQueryControllerTest {

    @Mock
    private AccountQueryService accountQueryService;
    @Mock
    private PortfolioQueryService portfolioQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AssetQueryController(accountQueryService, portfolioQueryService)).build();
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
}
