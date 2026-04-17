package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 动态配置测试 Controller
 * 用于验证 Nacos 配置中心的动态刷新功能
 */
@RefreshScope
@RestController
@RequestMapping("/api/config")
public class ConfigTestController {

    @Value("${app.config.test.value:默认值}")
    private String testValue;

    @Value("${app.config.seckill.limit:100}")
    private Integer seckillLimit;

    @Value("${app.config.message.welcome:欢迎光临}")
    private String welcomeMessage;

    /**
     * 获取当前配置
     */
    @GetMapping("/current")
    public Map<String, Object> getCurrentConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("testValue", testValue);
        config.put("seckillLimit", seckillLimit);
        config.put("welcomeMessage", welcomeMessage);
        config.put("timestamp", System.currentTimeMillis());
        return config;
    }

    /**
     * 测试配置刷新
     */
    @GetMapping("/test")
    public String testConfig() {
        return String.format("当前测试值：%s, 秒杀限制：%d, 欢迎语：%s", 
                testValue, seckillLimit, welcomeMessage);
    }
}