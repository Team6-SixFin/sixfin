package com.sparta.learning.infrastructure.security;

import com.sparta.learning.global.exception.CustomException;
import com.sparta.learning.global.exception.LearningErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/* X-User-Role 헤더가 ROLE_ADMIN인 요청만 통과시킨다 (Trading의 AdminAuthorizationInterceptor와 동일 구조) */
@Component
public class AdminAuthorizationInterceptor implements HandlerInterceptor {

    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String role = request.getHeader(USER_ROLE_HEADER);

        if (!ADMIN_ROLE.equals(role)) {
            throw new CustomException(LearningErrorCode.ADMIN_FORBIDDEN);
        }
        return true;
    }
}
