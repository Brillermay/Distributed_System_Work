package com.example.demo.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.exception.SentinelGatewayBlockExceptionHandler;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Sentinel 熔断限流配置
 */
@Configuration
public class SentinelConfig {

    /**
     * 配置限流规则
     */
    @PostConstruct
    public void initGatewayRules() {
        Set<GatewayFlowRule> rules = new HashSet<>();
        
        // 对所有 API 进行限流：每秒最多 100 个请求
        rules.add(new GatewayFlowRule("seckill-service")
                .setCount(100) // 限流阈值
                .setIntervalSec(1) // 统计时间窗口（秒）
                .setBurst(50)); // 突发流量允许的最大请求数
        
        // 对秒杀接口单独限流：每秒最多 10 个请求
        rules.add(new GatewayFlowRule("seckill-api")
                .setCount(10)
                .setIntervalSec(1)
                .setBurst(5));
        
        // 对登录接口限流：防止暴力破解
        rules.add(new GatewayFlowRule("auth-api")
                .setCount(20)
                .setIntervalSec(60) // 每分钟最多 20 次
                .setBurst(5));
        
        GatewayRuleManager.loadRules(rules);
    }

    /**
     * 配置 API 分组
     */
    @PostConstruct
    public void initApiDefinitions() {
        Set<ApiDefinition> definitions = new HashSet<>();
        
        // 秒杀 API 分组
        ApiDefinition seckillApi = new ApiDefinition("seckill-api")
                .setPredicateItems(new HashSet<>() {{
                    add(new ApiPathPredicateItem().setPattern("/api/seckill/**"));
                    add(new ApiPathPredicateItem().setPattern("/api/order/seckill/**"));
                }});
        
        // 认证 API 分组
        ApiDefinition authApi = new ApiDefinition("auth-api")
                .setPredicateItems(new HashSet<>() {{
                    add(new ApiPathPredicateItem().setPattern("/api/auth/login"));
                    add(new ApiPathPredicateItem().setPattern("/api/auth/register"));
                }});
        
        definitions.add(seckillApi);
        definitions.add(authApi);
        
        GatewayApiDefinitionManager.loadApiDefinitions(definitions);
    }

    /**
     * 配置限流后的响应
     */
    @PostConstruct
    public void initBlockHandler() {
        BlockRequestHandler blockHandler = (serverWebExchange, throwable) -> {
            ServerHttpResponse response = serverWebExchange.getResponse();
            response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            
            String body = "{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\",\"data\":null}";
            return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes())));
        };
        
        GatewayCallbackManager.setBlockHandler(blockHandler);
    }

    /**
     * 注册 Sentinel Gateway Filter
     */
    @Bean
    @Ordered(Ordered.HIGHEST_PRECEDENCE)
    public SentinelGatewayFilter sentinelGatewayFilter() {
        return new SentinelGatewayFilter();
    }

    /**
     * 注册异常处理器
     */
    @Bean
    @Ordered(Ordered.HIGHEST_PRECEDENCE)
    public SentinelGatewayBlockExceptionHandler sentinelGatewayBlockExceptionHandler() {
        return new SentinelGatewayBlockExceptionHandler();
    }
}