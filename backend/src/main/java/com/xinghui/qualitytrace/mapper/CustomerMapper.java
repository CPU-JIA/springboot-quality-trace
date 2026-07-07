package com.xinghui.qualitytrace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinghui.qualitytrace.entity.Customer;
import org.apache.ibatis.annotations.Mapper;

/** 客户 Mapper —— 纯单表 CRUD */
@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {
}
