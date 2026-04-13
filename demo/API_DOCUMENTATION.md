# 秒杀系统 - 事务与一致性 API 文档

## 概述

本系统实现了以下事务与一致性保障机制：

1. **基于 Redis 的库存预扣减**：使用 Lua 脚本保证原子性，防止超卖和限购
2. **基于消息队列的最终一致性**：通过 Kafka 异步创建订单，实现流量削峰
3. **TCC 事务**：Try-Confirm-Cancel 模式保障分布式事务一致性
4. **订单支付一致性**：保证支付操作的幂等性和事务回滚

---

## 一、秒杀下单接口

### 1.1 提交秒杀订单（基于 Redis + 消息队列）

**接口**: `POST /api/seckill/submit`

**请求参数**:
```
userId: Long - 用户 ID
productId: Long - 商品 ID
```

**响应示例**:
```json
{
  "code": 200,
  "data": {
    "orderId": 123456789,
    "message": "下单成功，正在处理"
  },
  "message": "success"
}
```

**实现原理**:
1. 使用 Redis Lua 脚本原子性检查库存和限购
2. 预扣减 Redis 库存
3. 发送 Kafka 消息异步创建订单
4. 如果 Kafka 发送失败，自动回滚 Redis 预扣

**Lua 脚本逻辑**:
```lua
local stock = tonumber(redis.call('GET', KEYS[1]) or '-1')
if stock <= 0 then return 0 end  -- 库存不足
if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then return 2 end  -- 重复购买
redis.call('DECR', KEYS[1])  -- 扣减库存
redis.call('SADD', KEYS[2], ARGV[1])  -- 记录已购买用户
return 1
```

---

## 二、TCC 事务接口

### 2.1 TCC 事务下单

**接口**: `POST /api/order/tcc/submit`

**请求参数**:
```
userId: Long - 用户 ID
productId: Long - 商品 ID
```

**响应示例**:
```json
{
  "code": 200,
  "data": {
    "orderId": 123456789,
    "success": true,
    "message": "TCC 事务下单成功"
  },
  "message": "success"
}
```

**TCC 三阶段**:

#### Try 阶段（预扣减）
- 检查 Redis 库存是否充足
- 预扣减库存（DECR）
- 记录 TCC 事务状态为 TRY
- 设置 5 分钟过期时间

#### Confirm 阶段（确认）
- 确认库存扣减成功
- 删除 TCC 临时记录
- 更新事务状态为 CONFIRM

#### Cancel 阶段（取消）
- 回滚 Redis 库存（INCR）
- 删除 TCC 临时记录
- 更新事务状态为 CANCEL

### 2.2 查询 TCC 事务状态

**接口**: `GET /api/order/tcc/{transactionId}`

**路径参数**:
```
transactionId: Long - 事务 ID（订单 ID）
```

**响应示例**:
```json
{
  "code": 200,
  "data": {
    "transactionId": 123456789,
    "status": "CONFIRM",
    "transactionType": "SECKILL",
    "retryCount": 0
  },
  "message": "success"
}
```

**状态说明**:
- `TRY`: 尝试中（预扣减完成）
- `CONFIRM`: 已确认
- `CANCEL`: 已取消

---

## 三、订单管理接口

### 3.1 创建订单

**接口**: `POST /api/order/create`

**请求参数**:
```
userId: Long - 用户 ID
productId: Long - 商品 ID
count: Integer - 购买数量（默认 1）
```

**响应示例**:
```json
{
  "code": 200,
  "data": {
    "orderId": 123456789,
    "message": "订单创建成功"
  },
  "message": "success"
}
```

### 3.2 支付订单

**接口**: `POST /api/order/{orderId}/pay`

**路径参数**:
```
orderId: Long - 订单 ID
```

**响应示例**:
```json
{
  "code": 200,
  "data": {
    "orderId": 123456789,
    "success": true,
    "message": "支付成功"
  },
  "message": "success"
}
```

**幂等性保障**:
- 已支付的订单再次支付会返回成功（幂等返回）
- 使用数据库事务保证状态更新的原子性
- 只有状态为 0（待支付）的订单才能支付

### 3.3 取消订单

**接口**: `POST /api/order/{orderId}/cancel`

**路径参数**:
```
orderId: Long - 订单 ID
```

**响应示例**:
```json
{
  "code": 200,
  "data": {
    "orderId": 123456789,
    "success": true,
    "message": "订单已取消"
  },
  "message": "success"
}
```

**取消流程**:
1. 检查订单状态（必须为待支付）
2. 更新订单状态为已取消
3. 回滚 Redis 预扣库存（如果有）

### 3.4 查询订单详情

**接口**: `GET /api/order/{orderId}`

**响应示例**:
```json
{
  "code": 200,
  "data": {
    "id": 123456789,
    "userId": 1001,
    "productId": 2001,
    "buyCount": 1,
    "orderAmount": 9.90,
    "status": 1,
    "createTime": "2026-04-13T10:30:00"
  },
  "message": "success"
}
```

### 3.5 查询用户订单列表

**接口**: `GET /api/order/user/{userId}`

**响应示例**:
```json
{
  "code": 200,
  "data": [
    {
      "id": 123456789,
      "userId": 1001,
      "productId": 2001,
      "orderAmount": 9.90,
      "status": 1,
      "createTime": "2026-04-13T10:30:00"
    }
  ],
  "message": "success"
}
```

---

## 四、一致性保障机制

### 4.1 下单 + 库存扣减一致性

**方案**: Redis 预扣减 + Kafka 消息队列

**流程**:
1. Redis Lua 脚本原子性预扣减库存
2. 发送 Kafka 消息
3. 消费者异步创建订单并扣减数据库库存
4. 如果 Kafka 发送失败，回滚 Redis 预扣

**保障**:
- 防超卖：Redis 单线程 + Lua 脚本原子性
- 限购：Redis Set 记录已购买用户
- 最终一致性：消息队列 + 本地事务

### 4.2 订单支付 + 状态更新一致性

**方案**: 本地事务 + 幂等性设计

**保障**:
- 数据库事务保证订单状态更新的原子性
- 状态检查保证只有待支付订单可支付
- 幂等性设计允许重复调用支付接口

### 4.3 TCC 事务一致性

**方案**: Try-Confirm-Cancel 模式

**特点**:
- 两阶段提交变体，性能更好
- 支持事务恢复和补偿
- 定时任务扫描未完成事务

**恢复机制**:
- 定时任务每 5 分钟扫描一次
- 超过 5 分钟未完成的 TRY 状态事务会被恢复
- 最大重试次数 3 次，超过则执行 Cancel

---

## 五、数据库表结构

### 5.1 订单表 (order_info)
```sql
CREATE TABLE `order_info` (
  `id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `product_id` BIGINT NOT NULL,
  `buy_count` INT DEFAULT 1,
  `order_amount` DECIMAL(10,2),
  `status` TINYINT DEFAULT 0,  -- 0:待支付，1:已支付，2:已取消
  `create_time` DATETIME,
  `update_time` DATETIME,
  PRIMARY KEY (`id`)
);
```

### 5.2 TCC 事务记录表 (tcc_record)
```sql
CREATE TABLE `tcc_record` (
  `id` BIGINT NOT NULL,
  `transaction_id` BIGINT NOT NULL,
  `transaction_type` VARCHAR(32),  -- SECKILL/PAYMENT
  `status` VARCHAR(16),  -- TRY/CONFIRM/CANCEL
  `retry_count` INT DEFAULT 0,
  `request_data` TEXT,
  `create_time` DATETIME,
  `update_time` DATETIME,
  PRIMARY KEY (`id`)
);
```

---

## 六、使用示例

### 6.1 普通秒杀下单
```bash
# 提交秒杀订单
curl -X POST "http://localhost:8080/api/seckill/submit?userId=1001&productId=2001"

# 返回
{
  "code": 200,
  "data": {
    "orderId": 123456789,
    "message": "下单成功，正在处理"
  }
}
```

### 6.2 TCC 事务下单
```bash
# TCC 事务下单
curl -X POST "http://localhost:8080/api/order/tcc/submit?userId=1001&productId=2001"

# 查询 TCC 状态
curl "http://localhost:8080/api/order/tcc/123456789"
```

### 6.3 支付订单
```bash
# 支付订单
curl -X POST "http://localhost:8080/api/order/123456789/pay"

# 取消订单
curl -X POST "http://localhost:8080/api/order/123456789/cancel"
```

---

## 七、注意事项

1. **Redis 预热**: 系统启动时会自动将商品库存预热到 Redis
2. **消息队列**: 确保 Kafka 服务正常运行
3. **幂等性**: 所有写操作都支持幂等性，可安全重试
4. **超时处理**: TCC 事务 Try 阶段默认 5 分钟超时
5. **监控建议**: 建议监控 TCC 事务恢复任务的执行情况

---

## 八、技术栈

- **框架**: Spring Boot 3.3.10
- **数据库**: MySQL 8.0
- **缓存**: Redis
- **消息队列**: Kafka
- **ORM**: MyBatis-Plus
- **分布式事务**: TCC 模式