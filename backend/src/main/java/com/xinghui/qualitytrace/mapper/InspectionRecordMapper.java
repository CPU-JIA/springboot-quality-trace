package com.xinghui.qualitytrace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinghui.qualitytrace.entity.InspectionRecord;
import org.apache.ibatis.annotations.Mapper;

/** 检验记录明细 Mapper —— 单表 CRUD */
@Mapper
public interface InspectionRecordMapper extends BaseMapper<InspectionRecord> {
}
