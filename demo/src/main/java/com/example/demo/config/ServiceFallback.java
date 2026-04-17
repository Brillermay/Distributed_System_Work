package com.example.demo.config;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 服务降级和熔断处理
 */
@Component
public class ServiceFallback implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange)
                .onErrorResume(ex -> {
                    // 服务不可用时的降级处理
                    ServerHttpResponse response = exchange.getResponse();
                    response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    
                    String body = "{\"code\":503,\"message\":\"服务暂时不可用，请稍后重试\",\"data\":null}";
                    return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes())));
                });
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}