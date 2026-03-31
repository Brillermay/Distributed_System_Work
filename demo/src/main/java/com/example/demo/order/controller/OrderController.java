package com.example.demo.order.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.common.ApiResponse;
import com.example.demo.order.entity.OrderInfo;
import com.example.demo.order.mapper.OrderInfoMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final OrderInfoMapper orderInfoMapper;

    public OrderController(OrderInfoMapper orderInfoMapper) {
        this.orderInfoMapper = orderInfoMapper;
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderInfo> getByOrderId(@PathVariable Long orderId) {
        return ApiResponse.success(orderInfoMapper.selectById(orderId));
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<OrderInfo>> getByUserId(@PathVariable Long userId) {
        List<OrderInfo> list = orderInfoMapper.selectList(
                new LambdaQueryWrapper<OrderInfo>().eq(OrderInfo::getUserId, userId)
        );
        return ApiResponse.success(list);
    }
}