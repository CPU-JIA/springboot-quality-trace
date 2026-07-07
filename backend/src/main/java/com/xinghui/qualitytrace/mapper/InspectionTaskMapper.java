package com.xinghui.qualitytrace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinghui.qualitytrace.entity.InspectionTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 检验任务 Mapper —— 单表 CRUD + 高风险事务辅助查询 */
@Mapper
public interface InspectionTaskMapper extends BaseMapper<InspectionTask> {

    /** 按主键查询并加行锁，用于领取/提交检验任务时防止重复提交。 */
    @Select("""
            SELECT id, task_no, inspect_type, batch_id, process_record_id, status,
                   conclusion, inspector_id, assigned_at, completed_at, remark,
                   created_at, updated_at
            FROM inspection_task
            WHERE id = #{id}
            FOR UPDATE
            """)
    InspectionTask selectByIdForUpdate(@Param("id") Long id);
}
