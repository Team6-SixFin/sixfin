package com.sparta.learning.infrastructure.config;

import com.sparta.learning.infrastructure.security.AdminAuthorizationInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AdminAuthorizationInterceptor adminAuthorizationInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthorizationInterceptor)
                .addPathPatterns("/api/learnings/admin/**");
    }
}
