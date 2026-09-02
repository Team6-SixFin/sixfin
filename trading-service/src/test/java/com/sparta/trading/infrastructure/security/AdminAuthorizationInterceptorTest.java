package com.sparta.trading.infrastructure.security;

import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuthorizationInterceptorTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private final AdminAuthorizationInterceptor interceptor = new AdminAuthorizationInterceptor();

    @Test
    @DisplayName("ROLE_ADMIN이면 통과시킨다")
    void preHandle_passesWhenRoleIsAdmin() {
        when(request.getHeader("X-User-Role")).thenReturn("ROLE_ADMIN");

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("ROLE_USER면 거부한다")
    void preHandle_rejectsWhenRoleIsUser() {
        when(request.getHeader("X-User-Role")).thenReturn("ROLE_USER");

        CustomException exception = assertThrows(CustomException.class,
                () -> interceptor.preHandle(request, response, new Object()));

        assertThat(exception.getErrorCode()).isEqualTo(TradingErrorCode.MARKET_CLOCK_FORBIDDEN);
    }

    @Test
    @DisplayName("헤더가 없으면 거부한다")
    void preHandle_rejectsWhenHeaderMissing() {
        when(request.getHeader("X-User-Role")).thenReturn(null);

        CustomException exception = assertThrows(CustomException.class,
                () -> interceptor.preHandle(request, response, new Object()));

        assertThat(exception.getErrorCode()).isEqualTo(TradingErrorCode.MARKET_CLOCK_FORBIDDEN);
    }
}
