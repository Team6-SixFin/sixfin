package com.sparta.trading.presentation.controller.position;

import com.sparta.trading.application.service.PositionQueryService;
import com.sparta.trading.domain.entity.PositionStatus;
import com.sparta.trading.global.exception.GlobalExceptionHandler;
import com.sparta.trading.global.response.PageResponse;
import com.sparta.trading.presentation.dto.response.PositionDetailResponse;
import com.sparta.trading.presentation.dto.response.PositionResponse;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PositionQueryControllerTest {

    @Mock
    private PositionQueryService positionQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PositionQueryController(positionQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void getPositions_usesDefaultOpenStatusAndPageable() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();
        PositionResponse positionResponse = positionResponseOf(positionId, PositionStatus.OPEN);

        when(positionQueryService.getPositions(eq(userId), eq(PositionStatus.OPEN), any(Pageable.class)))
                .thenReturn(pageResponseOf(positionResponse, 0, 20, 1));

        mockMvc.perform(get("/api/trading/positions").header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].positionId").value(positionId.toString()))
                .andExpect(jsonPath("$.content[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.content[0].status").value("OPEN"))
                .andExpect(jsonPath("$.content[0].currentPrice").value(220.0))
                .andExpect(jsonPath("$.content[0].unrealizedProfit").value(200.0))
                .andExpect(jsonPath("$.pageInfo.paginationType").value("OFFSET"))
                .andExpect(jsonPath("$.pageInfo.page").value(0))
                .andExpect(jsonPath("$.pageInfo.size").value(20));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(positionQueryService).getPositions(
                eq(userId),
                eq(PositionStatus.OPEN),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void getPositions_delegatesRequestedStatusAndPageable() throws Exception {
        UUID userId = UUID.randomUUID();

        when(positionQueryService.getPositions(eq(userId), eq(PositionStatus.CLOSED), any(Pageable.class)))
                .thenReturn(pageResponseOf(null, 1, 30, 0));

        mockMvc.perform(get("/api/trading/positions")
                        .header("X-User-Id", userId)
                        .param("status", "CLOSED")
                        .param("page", "1")
                        .param("size", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.pageInfo.page").value(1))
                .andExpect(jsonPath("$.pageInfo.size").value(30));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(positionQueryService).getPositions(
                eq(userId),
                eq(PositionStatus.CLOSED),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(30);
    }

    @Test
    void getPositions_rejectsRequestWithoutUserIdHeader() throws Exception {
        mockMvc.perform(get("/api/trading/positions"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPositions_rejectsInvalidStatus() throws Exception {
        mockMvc.perform(get("/api/trading/positions")
                        .header("X-User-Id", UUID.randomUUID())
                        .param("status", "INVALID"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPositionDetail_delegatesUsingUserIdHeaderAndPositionId() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();

        when(positionQueryService.getPositionDetail(userId, positionId))
                .thenReturn(positionDetailResponseOf(positionId));

        mockMvc.perform(get("/api/trading/positions/{id}", positionId)
                        .header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionId").value(positionId.toString()))
                .andExpect(jsonPath("$.stockId").value(1))
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.currentPrice").value(220.0))
                .andExpect(jsonPath("$.unrealizedProfit").value(200.0))
                .andExpect(jsonPath("$.totalBuyQuantity").value(10))
                .andExpect(jsonPath("$.realizedProfit").value(0.0));

        verify(positionQueryService).getPositionDetail(userId, positionId);
    }

    @Test
    void getPositionDetail_rejectsRequestWithoutUserIdHeader() throws Exception {
        mockMvc.perform(get("/api/trading/positions/{id}", UUID.randomUUID()))
                .andExpect(status().isBadRequest());
    }

    private PositionResponse positionResponseOf(UUID positionId, PositionStatus status) {
        BigDecimal currentPrice = status == PositionStatus.OPEN ? new BigDecimal("220.0000") : null;
        BigDecimal unrealizedProfit = status == PositionStatus.OPEN ? new BigDecimal("200.0000") : null;

        return new PositionResponse(
                positionId,
                "AAPL",
                "Apple Inc.",
                status,
                10,
                new BigDecimal("200.0000"),
                currentPrice,
                unrealizedProfit,
                Instant.parse("2026-09-04T01:30:00Z"),
                null
        );
    }

    private PageResponse<PositionResponse> pageResponseOf(
            PositionResponse positionResponse,
            int page,
            int size,
            long totalElements
    ) {
        List<PositionResponse> content = positionResponse == null ? List.of() : List.of(positionResponse);

        return PageResponse.of(new PageImpl<>(
                content,
                PageRequest.of(page, size),
                totalElements
        ));
    }

    private PositionDetailResponse positionDetailResponseOf(UUID positionId) {
        return new PositionDetailResponse(
                positionId,
                1L,
                "AAPL",
                "Apple Inc.",
                PositionStatus.OPEN,
                10,
                new BigDecimal("200.0000"),
                new BigDecimal("220.0000"),
                new BigDecimal("200.0000"),
                new BigDecimal("190.0000"),
                "실적 개선 기대",
                10,
                0,
                new BigDecimal("0.0000"),
                Instant.parse("2026-09-04T01:30:00Z"),
                null
        );
    }
}
