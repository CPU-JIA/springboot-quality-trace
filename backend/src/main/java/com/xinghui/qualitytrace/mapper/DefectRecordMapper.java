package com.xinghui.qualitytrace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinghui.qualitytrace.entity.DefectRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 缺陷记录 Mapper —— 单表 CRUD + 高风险事务辅助查询 */
@Mapper
public interface DefectRecordMapper extends BaseMapper<DefectRecord> {

    /** 按主键查询并加行锁，用于缺陷处置时防止重复提交。 */
    @Select("""
            SELECT id, defect_no, batch_id, process_record_id, inspection_task_id,
                   defect_type, severity, quantity, description, handle_method,
                   handle_status, handler_id, handled_at, created_at, updated_at
            FROM defect_record
            WHERE id = #{id}
            FOR UPDATE
            """)
    DefectRecord selectByIdForUpdate(@Param("id") Long id);
}
