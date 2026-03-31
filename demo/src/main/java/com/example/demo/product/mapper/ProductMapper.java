package com.example.demo.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.product.entity.Product;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ProductMapper extends BaseMapper<Product> {
    @Update("UPDATE product SET stock = stock - 1 WHERE id = #{productId} AND stock > 0")
    int deductStock(@Param("productId") Long productId);
}