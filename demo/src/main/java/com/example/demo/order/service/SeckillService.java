package com.example.demo.order.service;

import com.example.demo.order.entity.TccRecord;

public interface SeckillService {
    /**
     * 提交秒杀订单（基于 Redis 预扣减 + 消息队列）
     */
    Long submit(Long userId, Long productId);
    
    /**
     * TCC 事务：Try 阶段 - 预扣减库存
     */
    boolean tryLockStock(Long orderId, Long userId, Long productId);
    
    /**
     * TCC 事务：Confirm 阶段 - 确认扣减库存
     */
    void confirmStock(Long orderId);
    
    /**
     * TCC 事务：Cancel 阶段 - 回滚库存
     */
    void cancelStock(Long orderId);
    
    /**
     * 支付订单
     */
    boolean payOrder(Long orderId);
    
    /**
     * 取消订单
     */
    boolean cancelOrder(Long orderId);
}