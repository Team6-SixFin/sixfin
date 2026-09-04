package com.sparta.trading.presentation.controller;

import com.sparta.trading.application.service.TradingCommandService;
import com.sparta.trading.presentation.dto.request.PlaceOrderRequest;
import com.sparta.trading.presentation.dto.response.OrderResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("api/trading")
@RequiredArgsConstructor
public class TradingCommandController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final TradingCommandService tradingCommandService;

    @PostMapping("/orders")
    public OrderResponse placeOrder(@RequestHeader(USER_ID_HEADER) UUID userId,
                                    @Valid @RequestBody PlaceOrderRequest request) {
        return tradingCommandService.placeOrder(userId, request.toCommand());
    }
}
