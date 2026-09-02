package com.sparta.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Gateway 전역 JWT 인증 및 블랙리스트 검증 커스텀 필터
 * - Redis 예외 발생 시 .onErrorReturn(false)를 통해 토큰 서명 검증 흐름으로 우회 처리
 */
@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final SecretKey key;

    // 인증 제외 화이트리스트 경로
    private static final List<String> WHITE_LIST = List.of(
            "/api/auth/signup",
            "/api/auth/login",
            "/actuator"
    );

    public JwtAuthenticationFilter(ReactiveStringRedisTemplate redisTemplate,
                                   @Value("${jwt.secret}") String secretKey) {
        super(Config.class);
        this.redisTemplate = redisTemplate;
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public static class Config {}

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();

            // 1. 화이트리스트 경로는 JWT 검증 스킵
            if (WHITE_LIST.stream().anyMatch(path::startsWith)) {
                return chain.filter(exchange);
            }

            // 2. Authorization Header 존재 및 Bearer 포맷 검증 (WebFlux 규격)
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return onError(exchange, "Invalid authorization header format", HttpStatus.UNAUTHORIZED);
            }

            String token = authHeader.substring(7);

            // 3. Redis 블랙리스트 조회 및 JWT 서명 검증
            return redisTemplate.hasKey("BL:" + token)
                    .onErrorReturn(false) // Redis 다운/미연동 시 false(차단 안 함)로 간주하고 JWT 서명 검증 진행
                    .flatMap(isBlacklisted -> {
                        if (Boolean.TRUE.equals(isBlacklisted)) {
                            return onError(exchange, "Blacklisted token", HttpStatus.UNAUTHORIZED);
                        }
                        try {
                            Claims claims = Jwts.parser()
                                    .verifyWith(key)
                                    .build()
                                    .parseSignedClaims(token)
                                    .getPayload();

                            String userId = claims.getSubject();
                            String role = claims.get("role", String.class);

                            // 하위 마이크로서비스로 전달할 인가 헤더 주입
                            ServerHttpRequest modifiedRequest = request.mutate()
                                    .header("X-User-Id", userId)
                                    .header("X-User-Role", role)
                                    .build();

                            return chain.filter(exchange.mutate().request(modifiedRequest).build());
                        } catch (Exception e) {
                            return onError(exchange, "Invalid JWT token", HttpStatus.UNAUTHORIZED);
                        }
                    });
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        exchange.getResponse().setStatusCode(httpStatus);
        return exchange.getResponse().setComplete();
    }
}