package com.example.demo.order.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.common.ApiResponse;
import com.example.demo.order.entity.OrderInfo;
import com.example.demo.order.mapper.OrderInfoMapper;
import com.example.demo.order.service.PaymentService;
import com.example.demo.order.service.SeckillService;
import com.example.demo.order.service.TccCoordinatorService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final OrderInfoMapper orderInfoMapper;
    private final SeckillService seckillService;
    private final PaymentService paymentService;
    private final TccCoordinatorService tccCoordinatorService;

    public OrderController(OrderInfoMapper orderInfoMapper,
                          SeckillService seckillService,
                          PaymentService paymentService,
                          TccCoordinatorService tccCoordinatorService) {
        this.orderInfoMapper = orderInfoMapper;
        this.seckillService = seckillService;
        this.paymentService = paymentService;
        this.tccCoordinatorService = tccCoordinatorService;
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderInfo> getByOrderId(@PathVariable Long orderId) {
        return ApiResponse.success(orderInfoMapper.selectById(orderId));
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<OrderInfo>> getByUserId(@PathVariable Long userId) {
        List<OrderInfo> list = seckillService.getUserOrders(userId);
        return ApiResponse.success(list);
    }
    
    /**
     * 支付订单
     */
    @PostMapping("/{orderId}/pay")
    public ApiResponse<Map<String, Object>> payOrder(@PathVariable Long orderId) {
        PaymentService.PaymentResult result = paymentService.pay(orderId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("orderId", orderId);
        response.put("success", result.isSuccess());
        response.put("message", result.getMessage());
        
        if (result.isSuccess()) {
            return ApiResponse.success(response);
        } else {
            return ApiResponse.fail(result.getMessage());
        }
    }
    
    /**
     * 取消订单
     */
    @PostMapping("/{orderId}/cancel")
    public ApiResponse<Map<String, Object>> cancelOrder(@PathVariable Long orderId) {
        PaymentService.PaymentResult result = paymentService.cancel(orderId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("orderId", orderId);
        response.put("success", result.isSuccess());
        response.put("message", result.getMessage());
        
        if (result.isSuccess()) {
            return ApiResponse.success(response);
        } else {
            return ApiResponse.fail(result.getMessage());
        }
    }
    
    /**
     * 创建订单（用于 TCC 事务）
     */
    @PostMapping("/create")
    public ApiResponse<Map<String, Object>> createOrder(@RequestParam Long userId, 
                                                        @RequestParam Long productId,
                                                        @RequestParam(defaultValue = "1") Integer count) {
        try {
            Long orderId = seckillService.createOrder(userId, productId, count);
            
            Map<String, Object> response = new HashMap<>();
            response.put("orderId", orderId);
            response.put("message", "订单创建成功");
            
            return ApiResponse.success(response);
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }
    
    /**
     * TCC 事务下单接口
     */
    @PostMapping("/tcc/submit")
    public ApiResponse<Map<String, Object>> tccSubmit(@RequestParam Long userId,
                                                      @RequestParam Long productId) {
        try {
            // 1. 创建订单
            Long orderId = seckillService.createOrder(userId, productId, 1);
            
            // 2. 执行 TCC 事务
            boolean success = tccCoordinatorService.executeTcc(orderId, "SECKILL", userId, productId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("orderId", orderId);
            response.put("success", success);
            
            if (success) {
                response.put("message", "TCC 事务下单成功");
                return ApiResponse.success(response);
            } else {
                response.put("message", "TCC 事务下单失败");
                return ApiResponse.fail(response.get("message").toString());
            }
            
        } catch (Exception e) {
            return ApiResponse.fail("TCC 事务异常：" + e.getMessage());
        }
    }
    
    /**
     * 查询 TCC 事务状态
     */
    @GetMapping("/tcc/{transactionId}")
    public ApiResponse<Map<String, Object>> getTccStatus(@PathVariable Long transactionId) {
        var record = tccCoordinatorService.getTccRecord(transactionId);
        
        Map<String, Object> response = new HashMap<>();
        if (record != null) {
            response.put("transactionId", record.getTransactionId());
            response.put("status", record.getStatus());
            response.put("transactionType", record.getTransactionType());
            response.put("retryCount", record.getRetryCount());
        } else {
            response.put("message", "未找到 TCC 事务记录");
        }
        
        return ApiResponse.success(response);
    }
}