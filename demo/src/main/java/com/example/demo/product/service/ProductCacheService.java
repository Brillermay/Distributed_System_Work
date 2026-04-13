package com.example.demo.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.product.entity.Product;
import com.example.demo.product.mapper.ProductMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 商品缓存服务
 * 负责商品缓存管理及库存预热到 Redis 中
 */
@Service
public class ProductCacheService implements CommandLineRunner {
    
    private static final Logger log = LoggerFactory.getLogger(ProductCacheService.class);
    
    private final ProductMapper productMapper;
    private final StringRedisTemplate redisTemplate;

    public ProductCacheService(ProductMapper productMapper, StringRedisTemplate redisTemplate) {
        this.productMapper = productMapper;
        this.redisTemplate = redisTemplate;
    }
    
    /**
     * 应用启动时预热商品库存到 Redis
     */
    @Override
    public void run(String... args) {
        log.info("开始预热商品库存到 Redis");
        
        List<Product> products = productMapper.selectList(
            new LambdaQueryWrapper<Product>().eq(Product::getStatus, 1)
        );
        
        for (Product product : products) {
            String stockKey = "seckill:stock:" + product.getId();
            String boughtKey = "seckill:bought:" + product.getId();
            
            redisTemplate.opsForValue().set(stockKey, String.valueOf(product.getStock()));
            redisTemplate.delete(boughtKey);
            
            log.info("商品 ID={}, 名称={}, 库存={} 已预热到 Redis", 
                    product.getId(), product.getName(), product.getStock());
        }
        
        log.info("商品库存预热完成");
    }
    
    /**
     * 手动刷新某个商品的库存到 Redis
     */
    public void refreshProductStock(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product != null) {
            String stockKey = "seckill:stock:" + productId;
            redisTemplate.opsForValue().set(stockKey, String.valueOf(product.getStock()));
            log.info("商品 ID={} 库存已刷新到 Redis", productId);
        }
    }
    
    /**
     * 从 Redis 获取商品库存
     */
    public Integer getProductStockFromRedis(Long productId) {
        String stockKey = "seckill:stock:" + productId;
        String stock = redisTemplate.opsForValue().get(stockKey);
        return stock != null ? Integer.parseInt(stock) : null;
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