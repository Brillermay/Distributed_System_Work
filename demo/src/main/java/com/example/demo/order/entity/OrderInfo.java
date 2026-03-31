package com.example.demo.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;

@TableName("order_info")
public class OrderInfo {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long userId;
    private Long productId;
    private Integer buyCount;
    private BigDecimal orderAmount;
    private Integer status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Integer getBuyCount() { return buyCount; }
    public void setBuyCount(Integer buyCount) { this.buyCount = buyCount; }
    public BigDecimal getOrderAmount() { return orderAmount; }
    public void setOrderAmount(BigDecimal orderAmount) { this.orderAmount = orderAmount; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}