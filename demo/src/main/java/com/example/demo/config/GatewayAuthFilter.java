package com.example.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway 全局过滤器 - JWT 鉴权和请求日志
 */
@Component
public class GatewayAuthFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(GatewayAuthFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        
        // 记录请求日志
        logger.info("Gateway 请求 - Method: {}, Path: {}, IP: {}", 
                request.getMethod(), 
                path,
                request.getRemoteAddress());

        // 跳过认证的路径
        if (path.startsWith("/api/auth/login") || 
            path.startsWith("/api/auth/register") ||
            path.startsWith("/actuator") ||
            path.startsWith("/api/product")) {
            return chain.filter(exchange);
        }

        // 检查 JWT Token
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "缺少有效的认证令牌");
        }

        String token = authHeader.substring(7);
        
        // TODO: 这里可以添加 JWT 验证逻辑
        // 目前只是简单检查 token 是否存在
        if (token.isEmpty()) {
            return unauthorized(exchange, "Token 为空");
        }

        // 将用户信息传递给下游服务（通过 header）
        ServerHttpRequest modifiedRequest = request.mutate()
                .header("X-User-Token", token)
                .build();

        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        
        String body = "{\"code\":401,\"message\":\"" + message + "\"}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes())));
    }

    @Override
    public int getOrder() {
        return -100; // 高优先级
    }
}