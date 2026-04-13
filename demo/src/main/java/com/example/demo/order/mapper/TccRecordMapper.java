package com.example.demo.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.order.entity.TccRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * TCC 事务记录 Mapper
 */
@Mapper
public interface TccRecordMapper extends BaseMapper<TccRecord> {
}