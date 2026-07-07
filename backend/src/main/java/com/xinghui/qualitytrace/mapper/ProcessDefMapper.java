package com.xinghui.qualitytrace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinghui.qualitytrace.entity.ProcessDef;
import org.apache.ibatis.annotations.Mapper;

/** 工序定义 Mapper —— 纯单表 CRUD */
@Mapper
public interface ProcessDefMapper extends BaseMapper<ProcessDef> {
}
