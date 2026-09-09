package com.sparta.learning.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AiClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        // 스프링 부트 버전에 구애받지 않는 표준 팩토리 사용
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); // 연결 타임아웃 (5초)
        factory.setReadTimeout(10000);       // 응답 대기 타임아웃

        return RestClient.builder().requestFactory(factory);
    }
}