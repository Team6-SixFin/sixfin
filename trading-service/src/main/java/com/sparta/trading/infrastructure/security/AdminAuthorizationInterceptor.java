package com.sparta.trading.infrastructure.security;

import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** X-User-Role 헤더가 ROLE_ADMIN인 요청만 통과시킨다. */
@Component
public class AdminAuthorizationInterceptor implements HandlerInterceptor {

    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String role = request.getHeader("X-User-Role");
        if (!ADMIN_ROLE.equals(role)) {
            throw new CustomException(TradingErrorCode.MARKET_CLOCK_FORBIDDEN);
        }
        return true;
    }
}
