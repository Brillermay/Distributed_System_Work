package com.example.demo.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.user.entity.User; // 占位，替换成你的 Product 实体
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ProductCacheService {

    private final StringRedisTemplate redisTemplate;
    // private final ProductMapper productMapper;

    public ProductCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String getProductDetail(Long productId) {
        String key = "product:detail:" + productId;
        String lockKey = "lock:product:" + productId;

        // 1. 读缓存
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            if ("NULL".equals(cached)) return null; // 穿透防护：空值缓存
            return cached;
        }

        // 2. 击穿防护：互斥锁
        String lockVal = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, lockVal, Duration.ofSeconds(10));
        if (Boolean.TRUE.equals(locked)) {
            try {
                // 双检
                String again = redisTemplate.opsForValue().get(key);
                if (again != null) {
                    if ("NULL".equals(again)) return null;
                    return again;
                }

                // 3. 查数据库（这里用伪代码）
                // Product p = productMapper.selectById(productId);
                String dbJson = null; // 替换为真实查询后的 JSON

                if (dbJson == null) {
                    redisTemplate.opsForValue().set(key, "NULL", Duration.ofMinutes(2)); // 空值短缓存
                    return null;
                }

                // 4. 雪崩防护：TTL 随机抖动
                int jitter = ThreadLocalRandom.current().nextInt(60, 300);
                redisTemplate.opsForValue().set(key, dbJson, Duration.ofMinutes(30).plusSeconds(jitter));
                return dbJson;
            } finally {
                String cur = redisTemplate.opsForValue().get(lockKey);
                if (lockVal.equals(cur)) redisTemplate.delete(lockKey);
            }
        } else {
            // 没拿到锁，短暂等待后重试一次
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            String retry = redisTemplate.opsForValue().get(key);
            if (retry == null || "NULL".equals(retry)) return null;
            return retry;
        }
    }
}