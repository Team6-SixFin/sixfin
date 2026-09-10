package com.sparta.trading.presentation.controller.position;

import com.sparta.trading.application.service.PositionsQueryService;
import com.sparta.trading.domain.entity.PositionStatus;
import com.sparta.trading.global.response.PageResponse;
import com.sparta.trading.global.util.PageableUtil;
import com.sparta.trading.presentation.dto.response.PositionDetailResponse;
import com.sparta.trading.presentation.dto.response.PositionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/trading/positions")
public class PositionQueryController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final PositionsQueryService positionQueryService;

    // 포지션 목록 조회
    @GetMapping
    public PageResponse<PositionResponse> getPositions(
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @RequestParam(name = "status", defaultValue = "OPEN") PositionStatus status,
            @PageableDefault(size = PageableUtil.DEFAULT_SIZE) Pageable pageable
    ) {
        return positionQueryService.getPositions(userId, status, pageable);
    }

    @GetMapping("/{id}")
    public PositionDetailResponse getPositionDetail(
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @PathVariable("id") UUID positionId
    ) {
        return positionQueryService.getPositionDetail(userId, positionId);
    }
}
