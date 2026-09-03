package com.sparta.trading.presentation.controller;

import com.sparta.trading.application.dto.command.PlaceOrderCommand;
import com.sparta.trading.application.service.TradingCommandService;
import com.sparta.trading.domain.entity.OrderStatus;
import com.sparta.trading.presentation.dto.response.OrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TradingCommandControllerTest {

    @Mock
    private TradingCommandService tradingCommandService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TradingCommandController(tradingCommandService)).build();
    }

    @Test
    void placeOrder_usesGatewayUserHeaderAndDefaultsOrderTypeToMarket() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(tradingCommandService.placeOrder(any(), any())).thenReturn(new OrderResponse(
                UUID.randomUUID(), requestId, UUID.randomUUID(), OrderStatus.FILLED, null,
                new OrderResponse.ExecutionResponse(UUID.randomUUID(), 2, new BigDecimal("100.0000"),
                        new BigDecimal("200.0000"), Instant.parse("2026-09-03T13:31:00Z")),
                new BigDecimal("99800.0000"), Instant.parse("2026-09-03T13:30:00Z"), 12L
        ));

        mockMvc.perform(post("/api/trading/orders")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"%s","symbol":" aapl ","side":"BUY","quantity":2}
                                """.formatted(requestId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FILLED"));

        ArgumentCaptor<PlaceOrderCommand> commandCaptor = ArgumentCaptor.forClass(PlaceOrderCommand.class);
        verify(tradingCommandService).placeOrder(org.mockito.ArgumentMatchers.eq(userId), commandCaptor.capture());
        assertThat(commandCaptor.getValue().orderType().name()).isEqualTo("MARKET");
    }

    @Test
    void placeOrder_rejectsInvalidQuantityBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/trading/orders")
                        .header("X-User-Id", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"%s","symbol":"AAPL","side":"BUY","quantity":0}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }
}
