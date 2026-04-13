package com.example.demo.order.service;

import com.example.demo.order.entity.TccRecord;
import com.example.demo.order.mapper.TccRecordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * TCC 事务协调器
 * 负责协调 TCC 事务的 Try、Confirm、Cancel 三个阶段
 */
@Service
public class TccCoordinatorService {
    
    private static final Logger log = LoggerFactory.getLogger(TccCoordinatorService.class);
    
    private final TccRecordMapper tccRecordMapper;
    private final SeckillService seckillService;
    private final ObjectMapper objectMapper;
    
    public TccCoordinatorService(TccRecordMapper tccRecordMapper,
                                 SeckillService seckillService,
                                 ObjectMapper objectMapper) {
        this.tccRecordMapper = tccRecordMapper;
        this.seckillService = seckillService;
        this.objectMapper = objectMapper;
    }
    
    /**
     * 执行 TCC 事务
     * @param transactionId 事务 ID
     * @param transactionType 事务类型
     * @param userId 用户 ID
     * @param productId 商品 ID
     * @return 事务是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean executeTcc(Long transactionId, String transactionType, 
                             Long userId, Long productId) {
        try {
            // 1. 创建 TCC 记录
            TccRecord record = new TccRecord();
            record.setId(transactionId);
            record.setTransactionId(transactionId);
            record.setTransactionType(transactionType);
            record.setStatus("TRY");
            record.setRetryCount(0);
            record.setCreateTime(LocalDateTime.now());
            record.setUpdateTime(LocalDateTime.now());
            
            tccRecordMapper.insert(record);
            
            // 2. 执行 Try 阶段
            boolean trySuccess = seckillService.tryLockStock(transactionId, userId, productId);
            
            if (!trySuccess) {
                log.error("TCC Try 失败，事务 ID: {}", transactionId);
                record.setStatus("CANCEL");
                record.setUpdateTime(LocalDateTime.now());
                tccRecordMapper.updateById(record);
                return false;
            }
            
            // 3. Try 成功，执行 Confirm 阶段
            seckillService.confirmStock(transactionId);
            
            record.setStatus("CONFIRM");
            record.setUpdateTime(LocalDateTime.now());
            tccRecordMapper.updateById(record);
            
            log.info("TCC 事务执行成功，事务 ID: {}", transactionId);
            return true;
            
        } catch (Exception e) {
            log.error("TCC 事务执行异常，事务 ID: {}", transactionId, e);
            // 4. 异常时执行 Cancel 阶段
            try {
                seckillService.cancelStock(transactionId);
                
                TccRecord record = tccRecordMapper.selectById(transactionId);
                if (record != null) {
                    record.setStatus("CANCEL");
                    record.setUpdateTime(LocalDateTime.now());
                    tccRecordMapper.updateById(record);
                }
            } catch (Exception cancelEx) {
                log.error("TCC Cancel 执行失败，事务 ID: {}", transactionId, cancelEx);
            }
            return false;
        }
    }
    
    /**
     * 恢复未完成的 TCC 事务（用于系统重启后的补偿）
     */
    public void recoverUnfinishedTcc() {
        List<TccRecord> unfinishedRecords = tccRecordMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TccRecord>()
                .in(TccRecord::getStatus, "TRY", "CONFIRM")
                .lt(TccRecord::getUpdateTime, LocalDateTime.now().minusMinutes(5))
        );
        
        for (TccRecord record : unfinishedRecords) {
            try {
                if (record.getRetryCount() >= 3) {
                    // 超过最大重试次数，执行 Cancel
                    log.warn("TCC 事务超过最大重试次数，执行 Cancel，事务 ID: {}", record.getTransactionId());
                    seckillService.cancelStock(record.getTransactionId());
                    record.setStatus("CANCEL");
                    record.setUpdateTime(LocalDateTime.now());
                    tccRecordMapper.updateById(record);
                    continue;
                }
                
                // 重试执行 Confirm
                log.info("恢复 TCC 事务，事务 ID: {}, 重试次数：{}", record.getTransactionId(), record.getRetryCount());
                seckillService.confirmStock(record.getTransactionId());
                
                record.setStatus("CONFIRM");
                record.setRetryCount(record.getRetryCount() + 1);
                record.setUpdateTime(LocalDateTime.now());
                tccRecordMapper.updateById(record);
                
            } catch (Exception e) {
                log.error("恢复 TCC 事务失败，事务 ID: {}", record.getTransactionId(), e);
            }
        }
    }
    
    /**
     * 查询 TCC 事务记录
     */
    public TccRecord getTccRecord(Long transactionId) {
        return tccRecordMapper.selectById(transactionId);
    }
}