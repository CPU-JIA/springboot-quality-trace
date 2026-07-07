package com.xinghui.qualitytrace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinghui.qualitytrace.entity.BatchOverview;
import org.apache.ibatis.annotations.Mapper;

/** 批次全景视图 Mapper —— 只读查询 v_batch_overview */
@Mapper
public interface BatchOverviewMapper extends BaseMapper<BatchOverview> {
}
