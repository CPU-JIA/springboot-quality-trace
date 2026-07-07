package com.xinghui.qualitytrace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinghui.qualitytrace.entity.Supplier;
import org.apache.ibatis.annotations.Mapper;

/** 供应商 Mapper —— 纯单表 CRUD（BaseMapper 提供全部能力） */
@Mapper
public interface SupplierMapper extends BaseMapper<Supplier> {
}
