# Nacos + Gateway + Sentinel 集成指南

## 目录
- [环境搭建](#环境搭建)
- [服务注册与发现](#服务注册与发现)
- [配置中心](#配置中心)
- [网关路由](#网关路由)
- [流量治理](#流量治理)
- [测试指南](#测试指南)

## 环境搭建

### 1. 启动所有服务

```bash
# 启动 Nacos、Sentinel、MySQL、Redis、Kafka 和应用服务
docker-compose up -d
```

### 2. 验证服务状态

```bash
# 查看所有容器状态
docker-compose ps

# 查看 Nacos 日志
docker logs -f seckill-nacos

# 查看 Sentinel 日志
docker logs -f seckill-sentinel
```

### 3. 访问管理界面

- **Nacos 控制台**: http://localhost:8848/nacos
  - 默认账号/密码：nacos/nacos
  
- **Sentinel 控制台**: http://localhost:8080
  - 默认账号/密码：sentinel/sentinel

## 服务注册与发现

### 1. 查看服务注册情况

访问 Nacos 控制台：
- 服务管理 -> 服务列表
- 应该能看到 `seckill-service` 服务，有两个实例（backend1 和 backend2）

### 2. 测试服务发现

```bash
# 获取服务列表
curl http://localhost:8081/api/discovery/services

# 获取服务实例
curl http://localhost:8081/api/discovery/instances/seckill-service

# 获取当前实例信息
curl http://localhost:8081/api/discovery/current
```

### 3. 测试负载均衡

多次调用以下接口，观察返回的实例信息（应该在 backend1 和 backend2 之间轮询）：

```bash
# 连续调用 10 次
for i in {1..10}; do
  curl http://localhost:8081/api/discovery/current | jq .
done
```

## 配置中心

### 1. 在 Nacos 创建配置

登录 Nacos 控制台 (http://localhost:8848/nacos)：

1. 进入 **配置管理** -> **配置列表**
2. 点击 **+** 号添加配置
3. 填写以下信息：
   - **Data ID**: `seckill-service.yaml`
   - **Group**: `DEFAULT_GROUP`
   - **配置格式**: `YAML`
   - **配置内容**: 复制 `src/main/resources/nacos/seckill-service.yaml` 的内容
4. 点击 **发布**

同样方式创建共享配置：
- **Data ID**: `common-config.yaml`
- **配置内容**: 复制 `src/main/resources/nacos/common-config.yaml` 的内容

### 2. 测试动态配置刷新

```bash
# 获取当前配置
curl http://localhost:8081/api/config/current | jq .

# 测试配置信息
curl http://localhost:8081/api/config/test
```

### 3. 动态更新配置

1. 在 Nacos 控制台修改 `seckill-service.yaml` 配置
2. 修改 `app.config.test.value` 的值，例如改为 "动态配置测试值"
3. 点击 **发布**
4. 再次调用接口，查看配置是否更新：

```bash
curl http://localhost:8081/api/config/current | jq .
```

**无需重启服务，配置应该立即生效！**

## 网关路由

### 1. 网关配置

Gateway 已配置以下路由规则：

| 路由 ID | 路径模式 | 目标服务 |
|--------|---------|---------|
| auth-service | /api/auth/** | lb://seckill-service |
| order-service | /api/order/**, /api/seckill/** | lb://seckill-service |
| product-service | /api/product/** | lb://seckill-service |

### 2. 通过网关调用服务

```bash
# 登录接口
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"test123"}'

# 获取商品列表（无需认证）
curl http://localhost:8080/api/product/list

# 秒杀接口（需要认证）
curl http://localhost:8080/api/seckill/1 \
  -H "Authorization: Bearer <your-token>"
```

### 3. 测试动态路由

Gateway 使用 `lb://` 协议，会自动从 Nacos 获取服务实例并进行负载均衡：

```bash
# 连续调用，观察负载均衡效果
for i in {1..10}; do
  curl http://localhost:8080/api/discovery/current | jq .
done
```

## 流量治理

### 1. Sentinel 限流规则

已配置的限流规则：

- **全局限流**: 每秒最多 100 个请求
- **秒杀接口**: 每秒最多 10 个请求
- **登录接口**: 每分钟最多 20 个请求

### 2. 测试限流

```bash
# 快速连续调用秒杀接口（超过限流阈值）
for i in {1..20}; do
  curl http://localhost:8080/api/seckill/1
done
```

当请求超过阈值时，会收到 429 错误：
```json
{
  "code": 429,
  "message": "请求过于频繁，请稍后再试"
}
```

### 3. 查看 Sentinel 监控

访问 Sentinel 控制台 (http://localhost:8080)：
- **实时监控**: 查看 QPS、响应时间等指标
- **簇点链路**: 查看各个接口的调用情况
- **流控规则**: 查看和管理限流规则

### 4. 动态配置流控规则

在 Nacos 控制台创建/修改配置：
- **Data ID**: `seckill-service-sentinel`
- **配置内容**: JSON 格式的流控规则

修改后，Sentinel 会自动应用新规则，**无需重启服务**！

### 5. 熔断降级测试

当服务出现异常或响应过慢时，Sentinel 会自动触发熔断：

```bash
# 服务降级响应
{
  "code": 503,
  "message": "服务暂时不可用，请稍后重试"
}
```

## 测试指南

### 完整测试流程

1. **启动环境**
   ```bash
   docker-compose up -d
   ```

2. **等待服务就绪**
   ```bash
   # 等待 30 秒让所有服务启动
   sleep 30
   ```

3. **验证服务注册**
   ```bash
   curl http://localhost:8081/api/discovery/services
   ```

4. **测试配置中心**
   ```bash
   curl http://localhost:8081/api/config/current
   ```

5. **测试网关路由**
   ```bash
   curl http://localhost:8080/api/product/list
   ```

6. **测试限流**
   ```bash
   for i in {1..20}; do curl http://localhost:8080/api/seckill/1; done
   ```

7. **查看监控**
   - Nacos: http://localhost:8848/nacos
   - Sentinel: http://localhost:8080

### 常见问题

#### 1. Nacos 连接失败
- 检查 Nacos 服务是否正常启动
- 检查网络配置，确保容器间可以通信
- 查看 Nacos 日志：`docker logs seckill-nacos`

#### 2. 配置不生效
- 确认 Nacos 中已发布配置
- 检查配置的 Data ID 和 Group 是否正确
- 查看应用日志，确认配置加载成功

#### 3. 限流不生效
- 确认 Sentinel 依赖已正确引入
- 检查 Sentinel 控制台是否有监控数据
- 查看流控规则配置是否正确

#### 4. 网关路由失败
- 检查服务是否已在 Nacos 注册
- 查看 Gateway 日志
- 确认路由配置是否正确

## 总结

通过以上配置，我们实现了：

✅ **服务注册与发现**: 使用 Nacos 作为注册中心  
✅ **配置中心**: 使用 Nacos Config 实现动态配置  
✅ **服务网关**: 使用 Spring Cloud Gateway 统一入口  
✅ **负载均衡**: Gateway 自动从 Nacos 获取服务实例  
✅ **限流**: Sentinel 实现接口级限流  
✅ **熔断降级**: Sentinel 实现服务熔断和降级  
✅ **动态规则**: 通过 Nacos 动态更新 Sentinel 规则  

所有功能都支持**热更新**，无需重启服务！