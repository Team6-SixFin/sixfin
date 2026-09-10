package com.sparta.trading.presentation.controller.cashledger;

import com.sparta.trading.application.service.CashLedgersQueryService;
import com.sparta.trading.domain.entity.CashLedgerTxType;
import com.sparta.trading.global.exception.GlobalExceptionHandler;
import com.sparta.trading.global.response.PageResponse;
import com.sparta.trading.presentation.dto.response.CashLedgerResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CashLedgerQueryControllerTest {

    @Mock
    private CashLedgersQueryService cashLedgerQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CashLedgerQueryController(cashLedgerQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void getCashLedgers_usesDefaultPageableAndReturnsPageResponse() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        CashLedgerResponse cashLedgerResponse = cashLedgerResponseOf(executionId, CashLedgerTxType.BUY);

        when(cashLedgerQueryService.getCashLedgers(eq(userId), isNull(), any(Pageable.class)))
                .thenReturn(pageResponseOf(cashLedgerResponse, 0, 20, 1));

        mockMvc.perform(get("/api/trading/cash-ledgers").header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ledgerId").value(42))
                .andExpect(jsonPath("$.content[0].executionId").value(executionId.toString()))
                .andExpect(jsonPath("$.content[0].txType").value("BUY"))
                .andExpect(jsonPath("$.content[0].amount").value(-2000.0))
                .andExpect(jsonPath("$.pageInfo.paginationType").value("OFFSET"))
                .andExpect(jsonPath("$.pageInfo.page").value(0))
                .andExpect(jsonPath("$.pageInfo.size").value(20))
                .andExpect(jsonPath("$.summary").value(nullValue()));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(cashLedgerQueryService).getCashLedgers(
                eq(userId),
                isNull(),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void getCashLedgers_delegatesRequestedTxTypeAndPageable() throws Exception {
        UUID userId = UUID.randomUUID();

        when(cashLedgerQueryService.getCashLedgers(
                eq(userId),
                eq(CashLedgerTxType.BUY),
                any(Pageable.class)
        )).thenReturn(pageResponseOf(null, 1, 30, 0));

        mockMvc.perform(get("/api/trading/cash-ledgers")
                        .header("X-User-Id", userId)
                        .param("txType", "BUY")
                        .param("page", "1")
                        .param("size", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.pageInfo.page").value(1))
                .andExpect(jsonPath("$.pageInfo.size").value(30));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(cashLedgerQueryService).getCashLedgers(
                eq(userId),
                eq(CashLedgerTxType.BUY),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(30);
    }

    @Test
    void getCashLedgers_rejectsRequestWithoutUserIdHeader() throws Exception {
        mockMvc.perform(get("/api/trading/cash-ledgers"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getCashLedgers_rejectsInvalidTxType() throws Exception {
        mockMvc.perform(get("/api/trading/cash-ledgers")
                        .header("X-User-Id", UUID.randomUUID())
                        .param("txType", "INVALID"))
                .andExpect(status().isBadRequest());
    }

    private CashLedgerResponse cashLedgerResponseOf(UUID executionId, CashLedgerTxType txType) {
        return new CashLedgerResponse(
                42L,
                executionId,
                txType,
                new BigDecimal("-2000.0000"),
                new BigDecimal("98000.0000"),
                Instant.parse("2026-09-07T01:30:00Z")
        );
    }

    private PageResponse<CashLedgerResponse> pageResponseOf(
            CashLedgerResponse cashLedgerResponse,
            int page,
            int size,
            long totalElements
    ) {
        List<CashLedgerResponse> content = cashLedgerResponse == null
                ? List.of()
                : List.of(cashLedgerResponse);

        return PageResponse.of(new PageImpl<>(
                content,
                PageRequest.of(page, size),
                totalElements
        ));
    }
}
