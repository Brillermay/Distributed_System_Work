package com.example.demo.order.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 支付服务
 * 处理订单支付的一致性保障，支持幂等性和事务回滚
 */
@Service
public class PaymentService {
    
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    
    private final SeckillService seckillService;
    
    public PaymentService(SeckillService seckillService) {
        this.seckillService = seckillService;
    }
    
    /**
     * 支付订单（保证幂等性）
     * @param orderId 订单 ID
     * @return 支付结果
     */
    @Transactional(rollbackFor = Exception.class)
    public PaymentResult pay(Long orderId) {
        try {
            // 1. 检查订单状态
            var order = seckillService.getOrderById(orderId);
            if (order == null) {
                return PaymentResult.fail("订单不存在");
            }
            
            // 2. 幂等性检查：已支付的订单直接返回成功
            if (order.getStatus() == 1) {
                log.info("订单已支付，幂等返回，orderId={}", orderId);
                return PaymentResult.success(orderId, "订单已支付");
            }
            
            // 3. 检查订单是否可支付（状态为 0：待支付）
            if (order.getStatus() != 0) {
                return PaymentResult.fail("订单状态异常，不能支付");
            }
            
            // 4. 执行支付（更新订单状态）
            boolean success = seckillService.payOrder(orderId);
            
            if (success) {
                log.info("订单支付成功，orderId={}", orderId);
                return PaymentResult.success(orderId, "支付成功");
            } else {
                log.error("订单支付失败，orderId={}", orderId);
                return PaymentResult.fail("支付失败");
            }
            
        } catch (Exception e) {
            log.error("支付异常，orderId={}", orderId, e);
            return PaymentResult.fail("支付异常：" + e.getMessage());
        }
    }
    
    /**
     * 取消订单并回滚库存
     * @param orderId 订单 ID
     * @return 取消结果
     */
    @Transactional(rollbackFor = Exception.class)
    public PaymentResult cancel(Long orderId) {
        try {
            boolean success = seckillService.cancelOrder(orderId);
            
            if (success) {
                log.info("订单取消成功，orderId={}", orderId);
                return PaymentResult.success(orderId, "订单已取消");
            } else {
                return PaymentResult.fail("订单取消失败");
            }
            
        } catch (Exception e) {
            log.error("取消订单异常，orderId={}", orderId, e);
            return PaymentResult.fail("取消异常：" + e.getMessage());
        }
    }
    
    /**
     * 支付结果
     */
    public static class PaymentResult {
        private boolean success;
        private Long orderId;
        private String message;
        
        public PaymentResult() {}
        
        public static PaymentResult success(Long orderId, String message) {
            PaymentResult result = new PaymentResult();
            result.success = true;
            result.orderId = orderId;
            result.message = message;
            return result;
        }
        
        public static PaymentResult fail(String message) {
            PaymentResult result = new PaymentResult();
            result.success = false;
            result.message = message;
            return result;
        }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}