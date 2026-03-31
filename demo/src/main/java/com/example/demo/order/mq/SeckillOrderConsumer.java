package com.example.demo.order.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.order.entity.OrderInfo;
import com.example.demo.order.entity.SeckillOrder;
import com.example.demo.order.mapper.OrderInfoMapper;
import com.example.demo.order.mapper.SeckillOrderMapper;
import com.example.demo.product.entity.Product;
import com.example.demo.product.mapper.ProductMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SeckillOrderConsumer {

    private final OrderInfoMapper orderInfoMapper;
    private final SeckillOrderMapper seckillOrderMapper;
    private final ProductMapper productMapper;

    public SeckillOrderConsumer(OrderInfoMapper orderInfoMapper,
                                SeckillOrderMapper seckillOrderMapper,
                                ProductMapper productMapper) {
        this.orderInfoMapper = orderInfoMapper;
        this.seckillOrderMapper = seckillOrderMapper;
        this.productMapper = productMapper;
    }

    @KafkaListener(topics = "seckill-order-topic", groupId = "seckill-order-group")
    @Transactional(rollbackFor = Exception.class)
    public void consume(SeckillOrderMessage msg) {
        if (orderInfoMapper.selectById(msg.getOrderId()) != null) return; // 消息幂等

        SeckillOrder existed = seckillOrderMapper.selectOne(
                new LambdaQueryWrapper<SeckillOrder>()
                        .eq(SeckillOrder::getUserId, msg.getUserId())
                        .eq(SeckillOrder::getProductId, msg.getProductId())
        );
        if (existed != null) return; // 业务幂等

        int updated = productMapper.deductStock(msg.getProductId());
        if (updated == 0) throw new RuntimeException("数据库库存不足");

        Product p = productMapper.selectById(msg.getProductId());
        if (p == null) throw new RuntimeException("商品不存在");

        OrderInfo order = new OrderInfo();
        order.setId(msg.getOrderId());
        order.setUserId(msg.getUserId());
        order.setProductId(msg.getProductId());
        order.setBuyCount(1);
        order.setOrderAmount(p.getSeckillPrice() != null ? p.getSeckillPrice() : p.getPrice());
        order.setStatus(0);
        orderInfoMapper.insert(order);

        SeckillOrder so = new SeckillOrder();
        so.setUserId(msg.getUserId());
        so.setProductId(msg.getProductId());
        so.setOrderId(msg.getOrderId());
        seckillOrderMapper.insert(so);
    }
}