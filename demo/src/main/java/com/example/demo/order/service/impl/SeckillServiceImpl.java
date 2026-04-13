package com.example.demo.order.service.impl;

import com.example.demo.common.id.SnowflakeIdGenerator;
import com.example.demo.order.entity.OrderInfo;
import com.example.demo.order.mapper.OrderInfoMapper;
import com.example.demo.order.mq.SeckillOrderMessage;
import com.example.demo.order.service.SeckillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Service
public class SeckillServiceImpl implements SeckillService {

    private static final Logger log = LoggerFactory.getLogger(SeckillServiceImpl.class);
    
    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, SeckillOrderMessage> kafkaTemplate;
    private final SnowflakeIdGenerator idGenerator;
    private final OrderInfoMapper orderInfoMapper;

    private static final String TOPIC = "seckill-order-topic";
    private static final String TCC_LOCK_KEY = "seckill:tcc:lock:";
    private static final String TCC_TRIED_KEY = "seckill:tcc:tried:";

    private static final DefaultRedisScript<Long> LUA_SUBMIT = new DefaultRedisScript<>(
            """
            local stock = tonumber(redis.call('GET', KEYS[1]) or '-1')
            if stock <= 0 then return 0 end
            if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then return 2 end
            redis.call('DECR', KEYS[1])
            redis.call('SADD', KEYS[2], ARGV[1])
            return 1
            """, Long.class
    );

    private static final DefaultRedisScript<Long> LUA_TRY_LOCK = new DefaultRedisScript<>(
            """
            local lockKey = KEYS[1]
            local triedKey = KEYS[2]
            local orderId = ARGV[1]
            local userId = ARGV[2]
            local productId = ARGV[3]
            
            if redis.call('EXISTS', triedKey) == 1 then
                return 2
            end
            
            local stock = tonumber(redis.call('GET', lockKey) or '0')
            if stock <= 0 then
                return 0
            end
            
            redis.call('DECR', lockKey)
            redis.call('SET', triedKey, orderId)
            redis.call('EXPIRE', triedKey, 300)
            return 1
            """, Long.class
    );

    public SeckillServiceImpl(StringRedisTemplate redisTemplate,
                              KafkaTemplate<String, SeckillOrderMessage> kafkaTemplate,
                              SnowflakeIdGenerator idGenerator,
                              OrderInfoMapper orderInfoMapper) {
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.idGenerator = idGenerator;
        this.orderInfoMapper = orderInfoMapper;
    }

    @Override
    public Long submit(Long userId, Long productId) {
        String stockKey = "seckill:stock:" + productId;
        String boughtKey = "seckill:bought:" + productId;

        Long ret = redisTemplate.execute(LUA_SUBMIT, Arrays.asList(stockKey, boughtKey), String.valueOf(userId));
        if (ret == null || ret == 0) throw new RuntimeException("库存不足");
        if (ret == 2) throw new RuntimeException("请勿重复下单");

        Long orderId = idGenerator.nextId();
        try {
            kafkaTemplate.send(TOPIC, String.valueOf(orderId), new SeckillOrderMessage(orderId, userId, productId))
                    .get(3, TimeUnit.SECONDS);
            return orderId;
        } catch (Exception e) {
            log.error("Kafka 发送失败，回滚 Redis 预扣", e);
            redisTemplate.opsForValue().increment(stockKey);
            redisTemplate.opsForSet().remove(boughtKey, String.valueOf(userId));
            throw new RuntimeException("系统繁忙，请重试");
        }
    }

    @Override
    public boolean tryLockStock(Long orderId, Long userId, Long productId) {
        String lockKey = "seckill:stock:" + productId;
        String triedKey = TCC_TRIED_KEY + orderId;
        
        Long ret = redisTemplate.execute(LUA_TRY_LOCK, 
                Arrays.asList(lockKey, triedKey), 
                String.valueOf(orderId), String.valueOf(userId), String.valueOf(productId));
        
        if (ret == null || ret == 0) {
            log.warn("TCC Try 失败：库存不足，orderId={}, productId={}", orderId, productId);
            return false;
        }
        if (ret == 2) {
            log.info("TCC Try 已执行过，orderId={}", orderId);
            return true;
        }
        
        log.info("TCC Try 成功，orderId={}, userId={}, productId={}", orderId, userId, productId);
        return true;
    }

    @Override
    public void confirmStock(Long orderId) {
        String triedKey = TCC_TRIED_KEY + orderId;
        
        if (redisTemplate.delete(triedKey)) {
            log.info("TCC Confirm 成功，orderId={}", orderId);
        } else {
            log.warn("TCC Confirm 未找到 Try 记录，orderId={}", orderId);
        }
    }

    @Override
    public void cancelStock(Long orderId) {
        String triedKey = TCC_TRIED_KEY + orderId;
        
        OrderInfo order = orderInfoMapper.selectById(orderId);
        if (order != null) {
            String stockKey = "seckill:stock:" + order.getProductId();
            redisTemplate.opsForValue().increment(stockKey);
            log.info("TCC Cancel 成功，回滚库存，orderId={}", orderId);
        }
        
        redisTemplate.delete(triedKey);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean payOrder(Long orderId) {
        OrderInfo order = orderInfoMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        
        if (order.getStatus() != 0) {
            log.warn("订单状态异常，不能支付，orderId={}, status={}", orderId, order.getStatus());
            return false;
        }
        
        order.setStatus(1);
        int updated = orderInfoMapper.updateById(order);
        
        if (updated > 0) {
            log.info("订单支付成功，orderId={}", orderId);
            return true;
        }
        
        return false;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelOrder(Long orderId) {
        OrderInfo order = orderInfoMapper.selectById(orderId);
        if (order == null) {
            return false;
        }
        
        if (order.getStatus() != 0) {
            log.warn("订单状态异常，不能取消，orderId={}, status={}", orderId, order.getStatus());
            return false;
        }
        
        order.setStatus(2);
        int updated = orderInfoMapper.updateById(order);
        
        if (updated > 0) {
            cancelStock(orderId);
            log.info("订单取消成功，orderId={}", orderId);
            return true;
        }
        
        return false;
    }
}