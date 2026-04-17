package com.example.demo.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway 路由配置
 */
@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // 认证服务路由
                .route("auth-service", r -> r
                        .path("/api/auth/**")
                        .uri("lb://seckill-service"))
                // 订单服务路由
                .route("order-service", r -> r
                        .path("/api/order/**", "/api/seckill/**")
                        .uri("lb://seckill-service"))
                // 产品服务路由
                .route("product-service", r -> r
                        .path("/api/product/**")
                        .uri("lb://seckill-service"))
                .build();
    }
}