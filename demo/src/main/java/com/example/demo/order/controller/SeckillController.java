package com.example.demo.order.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.order.service.SeckillService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/seckill")
public class SeckillController {

    private final SeckillService seckillService;

    public SeckillController(SeckillService seckillService) {
        this.seckillService = seckillService;
    }

    @PostMapping("/submit")
    public ApiResponse<Map<String, Object>> submit(@RequestParam Long userId, @RequestParam Long productId) {
        Long orderId = seckillService.submit(userId, productId);
        return ApiResponse.success(Map.of("orderId", orderId, "message", "下单成功，正在处理"));
    }
}