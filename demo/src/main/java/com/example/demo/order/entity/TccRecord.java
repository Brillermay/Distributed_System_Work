package com.example.demo.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * TCC 事务记录表
 * 用于记录 TCC 事务的执行状态，支持事务恢复和补偿
 */
@TableName("tcc_record")
public class TccRecord {
    
    @TableId(type = IdType.INPUT)
    private Long id;
    
    /**
     * 事务 ID（通常使用订单 ID）
     */
    private Long transactionId;
    
    /**
     * 事务类型：SECKILL-秒杀，PAYMENT-支付
     */
    private String transactionType;
    
    /**
     * 当前状态：TRY-尝试中，CONFIRM-已确认，CANCEL-已取消
     */
    private String status;
    
    /**
     * 重试次数
     */
    private Integer retryCount;
    
    /**
     * 请求参数（JSON 格式）
     */
    private String requestData;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    public TccRecord() {}
    
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }
    
    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    
    public String getRequestData() { return requestData; }
    public void setRequestData(String requestData) { this.requestData = requestData; }
    
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}