package com.example.demo.order.service.impl;

import com.example.demo.common.id.SnowflakeIdGenerator;
import com.example.demo.order.mq.SeckillOrderMessage;
import com.example.demo.order.service.SeckillService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Service
public class SeckillServiceImpl implements SeckillService {

    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, SeckillOrderMessage> kafkaTemplate;
    private final SnowflakeIdGenerator idGenerator;

    private static final String TOPIC = "seckill-order-topic";

    private static final DefaultRedisScript<Long> LUA = new DefaultRedisScript<>(
            """
            local stock = tonumber(redis.call('GET', KEYS[1]) or '-1')
            if stock <= 0 then return 0 end
            if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then return 2 end
            redis.call('DECR', KEYS[1])
            redis.call('SADD', KEYS[2], ARGV[1])
            return 1
            """, Long.class
    );

    public SeckillServiceImpl(StringRedisTemplate redisTemplate,
                              KafkaTemplate<String, SeckillOrderMessage> kafkaTemplate,
                              SnowflakeIdGenerator idGenerator) {
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.idGenerator = idGenerator;
    }

    @Override
    public Long submit(Long userId, Long productId) {
        String stockKey = "seckill:stock:" + productId;
        String boughtKey = "seckill:bought:" + productId;

        Long ret = redisTemplate.execute(LUA, Arrays.asList(stockKey, boughtKey), String.valueOf(userId));
        if (ret == null || ret == 0) throw new RuntimeException("库存不足");
        if (ret == 2) throw new RuntimeException("请勿重复下单");

        Long orderId = idGenerator.nextId();
        try {
            kafkaTemplate.send(TOPIC, String.valueOf(orderId), new SeckillOrderMessage(orderId, userId, productId))
                    .get(3, TimeUnit.SECONDS);
            return orderId;
        } catch (Exception e) {
            // Kafka发送失败，补偿回滚Redis预扣
            redisTemplate.opsForValue().increment(stockKey);
            redisTemplate.opsForSet().remove(boughtKey, String.valueOf(userId));
            throw new RuntimeException("系统繁忙，请重试");
        }
    }
}