package com.sparta.trading.presentation.controller.portfolio;

import com.sparta.trading.application.service.PortfolioQueryService;
import com.sparta.trading.presentation.dto.response.PortfolioResponse;
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
class PortfolioQueryControllerTest {

    @Mock
    private PortfolioQueryService portfolioQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PortfolioQueryController(portfolioQueryService)).build();
    }

    @Test
    void getMyPortfolio_delegatesUsingUserIdHeader() throws Exception {
        UUID userId = UUID.randomUUID();
        when(portfolioQueryService.getPortfolio(userId)).thenReturn(new PortfolioResponse(
                new BigDecimal("70000.0000"),
                new BigDecimal("31500.0000"),
                new BigDecimal("101500.0000"),
                new BigDecimal("1500.0000"),
                new BigDecimal("5.0000"),
                Instant.parse("2026-09-04T01:30:00Z")
        ));

        mockMvc.perform(get("/api/trading/portfolio/me").header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashBalance").value(70000.0))
                .andExpect(jsonPath("$.stockValuation").value(31500.0))
                .andExpect(jsonPath("$.totalAssets").value(101500.0))
                .andExpect(jsonPath("$.unrealizedProfit").value(1500.0))
                .andExpect(jsonPath("$.unrealizedReturnRate").value(5.0))
                .andExpect(jsonPath("$.marketTime").value("2026-09-04T01:30:00Z"));

        verify(portfolioQueryService).getPortfolio(userId);
    }

    @Test
    void getMyPortfolio_rejectsRequestWithoutUserIdHeader() throws Exception {
        mockMvc.perform(get("/api/trading/portfolio/me"))
                .andExpect(status().isBadRequest());
    }
}
