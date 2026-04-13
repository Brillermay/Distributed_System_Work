package com.example.demo.order.task;

import com.example.demo.order.service.TccCoordinatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * TCC 事务恢复定时任务
 * 定期扫描并恢复未完成的 TCC 事务
 */
@Component
public class TccRecoveryTask {
    
    private static final Logger log = LoggerFactory.getLogger(TccRecoveryTask.class);
    
    private final TccCoordinatorService tccCoordinatorService;
    
    public TccRecoveryTask(TccCoordinatorService tccCoordinatorService) {
        this.tccCoordinatorService = tccCoordinatorService;
    }
    
    /**
     * 每 5 分钟执行一次 TCC 事务恢复
     */
    @Scheduled(fixedRate = 300000)
    public void recoverTccTransactions() {
        log.info("开始执行 TCC 事务恢复任务");
        try {
            tccCoordinatorService.recoverUnfinishedTcc();
            log.info("TCC 事务恢复任务执行完成");
        } catch (Exception e) {
            log.error("TCC 事务恢复任务执行失败", e);
        }
    }
}