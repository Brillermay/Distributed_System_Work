# 秒杀系统 - 事务与一致性实现指南

## 项目概述

本项目在现有秒杀系统基础上，实现了完整的事务与一致性保障机制，包括：

1. ✅ **基于 Redis 的库存预扣减**（防超卖、限购）
2. ✅ **基于消息队列的最终一致性**（下单 + 库存扣减）
3. ✅ **TCC 分布式事务**（Try-Confirm-Cancel 模式）
4. ✅ **订单支付一致性**（幂等性 + 事务回滚）

---

## 技术架构

```
┌─────────────┐      ┌──────────────┐      ┌─────────────┐
│   用户请求   │ ───> │  Spring Boot  │ ───> │    Redis    │
└─────────────┘      └──────────────┘      │ (预扣库存)  │
                           │                └─────────────┘
                           │
                           ▼
                     ┌──────────────┐      ┌─────────────┐
                     │    Kafka     │ ───> │   MySQL     │
                     │ (异步解耦)    │      │ (订单/库存) │
                     └──────────────┘      └─────────────┘
```

---

## 核心功能实现

### 1. 基于 Redis 的库存预扣减

**实现位置**: `SeckillServiceImpl.submit()`

**核心代码**:
```java
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
```

**特点**:
- ✅ 使用 Lua 脚本保证原子性
- ✅ 防止超卖（库存检查 + 扣减原子操作）
- ✅ 限购（使用 Redis Set 记录已购买用户）
- ✅ 高性能（纯 Redis 操作，无数据库交互）

---

### 2. 基于消息队列的最终一致性

**实现位置**: `SeckillOrderConsumer.consume()`

**流程**:
```
1. Redis 预扣减成功
   ↓
2. 发送 Kafka 消息
   ↓
3. 消费者异步处理
   ↓
4. 数据库事务扣减库存 + 创建订单
```

**保障机制**:
- ✅ Kafka 消息可靠投递
- ✅ 消费者幂等性检查（防止重复消费）
- ✅ 本地事务保证订单 + 库存的原子性
- ✅ 失败补偿（Kafka 发送失败时回滚 Redis）

---

### 3. TCC 分布式事务

**实现位置**: `TccCoordinatorService`

**三阶段**:

#### Try 阶段
```java
public boolean tryLockStock(Long orderId, Long userId, Long productId) {
    // 1. 检查是否已执行过（幂等）
    // 2. 检查 Redis 库存
    // 3. 预扣减库存
    // 4. 记录 TCC 状态（设置 5 分钟过期）
}
```

#### Confirm 阶段
```java
public void confirmStock(Long orderId) {
    // 1. 删除 TCC 临时记录
    // 2. 确认事务完成
}
```

#### Cancel 阶段
```java
public void cancelStock(Long orderId) {
    // 1. 回滚 Redis 库存
    // 2. 删除 TCC 临时记录
}
```

**特点**:
- ✅ 支持事务恢复（定时任务扫描未完成事务）
- ✅ 最大重试次数限制（3 次）
- ✅ 超时自动回滚（5 分钟）

---

### 4. 订单支付一致性

**实现位置**: `PaymentService.pay()`

**幂等性设计**:
```java
@Transactional(rollbackFor = Exception.class)
public PaymentResult pay(Long orderId) {
    OrderInfo order = seckillService.getOrderById(orderId);
    
    // 幂等性检查：已支付直接返回成功
    if (order.getStatus() == 1) {
        return PaymentResult.success(orderId, "订单已支付");
    }
    
    // 状态检查：只有待支付订单可支付
    if (order.getStatus() != 0) {
        return PaymentResult.fail("订单状态异常");
    }
    
    // 更新订单状态
    seckillService.payOrder(orderId);
}
```

**特点**:
- ✅ 数据库事务保证原子性
- ✅ 幂等性设计（可安全重试）
- ✅ 状态机控制（防止状态混乱）

---

## 快速开始

### 1. 环境准备

确保以下服务运行：
```bash
# MySQL
mysql -u root -p

# Redis
redis-cli ping

# Kafka
kafka-console-consumer --bootstrap-server localhost:9092 --topic seckill-order-topic
```

### 2. 初始化数据库

```bash
mysql -u root -p < src/main/resources/sql/schema.sql
```

### 3. 启动应用

```bash
./mvnw spring-boot:run
```

### 4. 测试功能

```bash
# 赋予执行权限
chmod +x test-seckill.sh

# 运行测试脚本
./test-seckill.sh
```

---

## API 使用示例

### 场景 1: 普通秒杀下单

```bash
# 提交秒杀订单
curl -X POST "http://localhost:8080/api/seckill/submit?userId=1001&productId=2001"

# 响应
{
  "code": 200,
  "data": {
    "orderId": 123456789,
    "message": "下单成功，正在处理"
  }
}
```

### 场景 2: TCC 事务下单

```bash
# TCC 事务下单
curl -X POST "http://localhost:8080/api/order/tcc/submit?userId=1001&productId=2001"

# 查询 TCC 状态
curl "http://localhost:8080/api/order/tcc/123456789"
```

### 场景 3: 订单支付

```bash
# 支付订单
curl -X POST "http://localhost:8080/api/order/123456789/pay"

# 取消订单
curl -X POST "http://localhost:8080/api/order/123456789/cancel"
```

---

## 数据库表结构

### 核心表

1. **order_info** - 订单表
2. **seckill_order** - 秒杀订单表（防重复）
3. **tcc_record** - TCC 事务记录表
4. **product** - 商品表
5. **user** - 用户表

详细表结构见 `src/main/resources/sql/schema.sql`

---

## 监控与运维

### 1. 查看 Redis 库存

```bash
redis-cli
> GET seckill:stock:2001
```

### 2. 查看 TCC 事务状态

```sql
SELECT * FROM tcc_record WHERE transaction_id = 123456789;
```

### 3. 查看未完成的事务

```sql
SELECT * FROM tcc_record 
WHERE status IN ('TRY', 'CONFIRM') 
AND update_time < NOW() - INTERVAL 5 MINUTE;
```

---

## 性能优化建议

1. **Redis 集群**: 生产环境使用 Redis Cluster 提高可用性
2. **Kafka 分区**: 增加 topic 分区数提高并发
3. **数据库分表**: 订单表按用户 ID 分表
4. **限流降级**: 使用 Sentinel 等工具进行限流
5. **监控告警**: 监控 TCC 恢复任务执行情况

---

## 常见问题

### Q1: Redis 库存和数据库库存不一致？

**A**: 这是正常现象。Redis 是预扣减，最终一致性由 Kafka 消息队列保证。如果 Kafka 发送失败，会自动回滚 Redis 预扣。

### Q2: TCC 事务超时怎么办？

**A**: 定时任务会扫描超过 5 分钟未完成的 TCC 事务，自动执行 Confirm 或 Cancel。超过 3 次重试失败会强制 Cancel。

### Q3: 如何保证幂等性？

**A**: 
- 秒杀下单：Redis Set 记录已购买用户
- 订单支付：检查订单状态，已支付直接返回成功
- 消息消费：订单 ID 唯一性检查

---

## 参考文档

- [API 详细文档](API_DOCUMENTATION.md)
- [数据库表结构](src/main/resources/sql/schema.sql)
- [TCC 事务模式详解](https://en.wikipedia.org/wiki/TCC_(transaction_pattern))

---

## 总结

本系统实现了多层次的事务与一致性保障：

✅ **高性能**: Redis 预扣减 + 消息队列异步处理  
✅ **防超卖**: Lua 脚本原子性保证  
✅ **限购**: Redis Set 去重  
✅ **最终一致性**: Kafka 消息队列 + 本地事务  
✅ **分布式事务**: TCC 模式支持  
✅ **幂等性**: 全链路幂等设计  
✅ **可恢复**: 定时任务扫描补偿  

生产环境可根据业务需求选择合适的方案组合！