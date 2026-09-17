package com.sparta.trading.presentation.controller;

import com.sparta.trading.application.service.TradingQueryService;
import com.sparta.trading.global.exception.GlobalExceptionHandler;
import com.sparta.trading.global.response.SliceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TradingQueryControllerTest {

    @Mock
    private TradingQueryService tradingQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TradingQueryController(tradingQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void searchOrder_usesStableDefaultSortAndReturnsSliceResponse() throws Exception {
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20, Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        ));
        when(tradingQueryService.searchOrder(eq(userId), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(SliceResponse.of(new SliceImpl<>(List.of(), pageable, false)));

        mockMvc.perform(get("/api/trading/orders").header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.pageInfo.paginationType").value("OFFSET"))
                .andExpect(jsonPath("$.pageInfo.page").value(0))
                .andExpect(jsonPath("$.pageInfo.size").value(20))
                .andExpect(jsonPath("$.pageInfo.hasNext").value(false))
                .andExpect(jsonPath("$.pageInfo.totalElements").doesNotExist())
                .andExpect(jsonPath("$.pageInfo.totalPages").doesNotExist());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(tradingQueryService).searchOrder(
                eq(userId),
                isNull(),
                isNull(),
                isNull(),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getSort())
                .containsExactly(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                );
    }
}
