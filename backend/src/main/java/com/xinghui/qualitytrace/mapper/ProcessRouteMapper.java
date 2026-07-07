package com.xinghui.qualitytrace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinghui.qualitytrace.entity.ProcessRoute;
import org.apache.ibatis.annotations.Mapper;

/** 工艺路线 Mapper —— 纯单表 CRUD（整条替换在 Service 事务内以 delete+insert 实现） */
@Mapper
public interface ProcessRouteMapper extends BaseMapper<ProcessRoute> {
}
