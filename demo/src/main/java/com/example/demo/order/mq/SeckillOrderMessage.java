package com.example.demo.order.mq;

public class SeckillOrderMessage {
    private Long orderId;
    private Long userId;
    private Long productId;

    public SeckillOrderMessage() {}
    public SeckillOrderMessage(Long orderId, Long userId, Long productId) {
        this.orderId = orderId;
        this.userId = userId;
        this.productId = productId;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
}