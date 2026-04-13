-- 建库
CREATE DATABASE IF NOT EXISTS seckill_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE seckill_db;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` VARCHAR(64) NOT NULL COMMENT '用户名',
  `password` VARCHAR(128) NOT NULL COMMENT '密码(建议存加密后)',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态:1正常,0禁用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 商品表（为秒杀做准备）
CREATE TABLE IF NOT EXISTS `product` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `name` VARCHAR(128) NOT NULL COMMENT '商品名称',
  `price` DECIMAL(10,2) NOT NULL COMMENT '原价',
  `seckill_price` DECIMAL(10,2) DEFAULT NULL COMMENT '秒杀价',
  `stock` INT NOT NULL DEFAULT 0 COMMENT '库存',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态:1上架,0下架',
  `start_time` DATETIME DEFAULT NULL COMMENT '秒杀开始时间',
  `end_time` DATETIME DEFAULT NULL COMMENT '秒杀结束时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status_time` (`status`, `start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- 订单表（用户购买记录）
CREATE TABLE IF NOT EXISTS `order_info` (
  `id` BIGINT NOT NULL COMMENT '订单 ID',
  `user_id` BIGINT NOT NULL COMMENT '用户 ID',
  `product_id` BIGINT NOT NULL COMMENT '商品 ID',
  `buy_count` INT NOT NULL DEFAULT 1 COMMENT '购买数量',
  `order_amount` DECIMAL(10,2) NOT NULL COMMENT '订单金额',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态:0 待支付，1 已支付，2 已取消',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_status` (`status`),
  CONSTRAINT `fk_order_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_order_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- 秒杀订单表（防止重复秒杀：同一用户同一商品唯一）
CREATE TABLE IF NOT EXISTS `seckill_order` (
  `id` BIGINT NOT NULL COMMENT '秒杀订单 ID',
  `user_id` BIGINT NOT NULL COMMENT '用户 ID',
  `product_id` BIGINT NOT NULL COMMENT '商品 ID',
  `order_id` BIGINT NOT NULL COMMENT '关联普通订单 ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀订单表';

-- TCC 事务记录表（用于 TCC 事务的状态管理和恢复）
CREATE TABLE IF NOT EXISTS `tcc_record` (
  `id` BIGINT NOT NULL COMMENT '记录 ID',
  `transaction_id` BIGINT NOT NULL COMMENT '事务 ID（订单 ID）',
  `transaction_type` VARCHAR(32) NOT NULL COMMENT '事务类型：SECKILL-秒杀，PAYMENT-支付',
  `status` VARCHAR(16) NOT NULL COMMENT '状态：TRY-尝试中，CONFIRM-已确认，CANCEL-已取消',
  `retry_count` INT NOT NULL DEFAULT 0 COMMENT '重试次数',
  `request_data` TEXT COMMENT '请求参数（JSON 格式）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_transaction_id` (`transaction_id`),
  KEY `idx_status_time` (`status`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='TCC 事务记录表';

INSERT INTO product (id, name, stock, price)
VALUES (1001, '秒杀商品A', 100, 9.90)
ON DUPLICATE KEY UPDATE name = VALUES(name);