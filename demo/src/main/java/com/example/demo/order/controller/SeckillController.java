package com.example.demo.order.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.order.service.SeckillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 秒杀控制器
 * 提供基于 Redis 预扣减和消息队列的秒杀下单接口
 */
@RestController
@RequestMapping("/api/seckill")
public class SeckillController {

    private static final Logger log = LoggerFactory.getLogger(SeckillController.class);
    
    private final SeckillService seckillService;
    private final StringRedisTemplate redisTemplate;

    public SeckillController(SeckillService seckillService,
                            StringRedisTemplate redisTemplate) {
        this.seckillService = seckillService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 提交秒杀订单
     * 基于 Redis Lua 脚本实现原子性的库存预扣减，防止超卖和重复购买
     * 通过 Kafka 消息队列异步创建订单，实现流量削峰
     * 
     * @param userId 用户 ID
     * @param productId 商品 ID
     * @return 订单 ID 和处理消息
     */
    @PostMapping("/submit")
    public ApiResponse<Map<String, Object>> submit(@RequestParam Long userId, @RequestParam Long productId) {
        try {
            Long orderId = seckillService.submit(userId, productId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("orderId", orderId);
            response.put("message", "下单成功，正在处理");
            
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("秒杀下单失败，userId={}, productId={}", userId, productId, e);
            return ApiResponse.fail(e.getMessage());
        }
    }
    
    /**
     * 获取 Redis 中的商品库存
     * 
     * @param productId 商品 ID
     * @return 库存数量
     */
    @GetMapping("/stock/{productId}")
    public ApiResponse<Map<String, Object>> getStock(@PathVariable Long productId) {
        String stockKey = "seckill:stock:" + productId;
        String stock = seckillService.getClass().toString(); // TODO: 需要实现获取库存的方法
        
        Map<String, Object> response = new HashMap<>();
        response.put("productId", productId);
        response.put("stock", stock);
        
        return ApiResponse.success(response);
    }
}