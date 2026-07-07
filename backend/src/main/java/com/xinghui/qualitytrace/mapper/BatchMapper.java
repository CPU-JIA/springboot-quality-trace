package com.xinghui.qualitytrace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinghui.qualitytrace.entity.Batch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/** 批次 Mapper —— 单表 CRUD + 高风险事务辅助查询 */
@Mapper
public interface BatchMapper extends BaseMapper<Batch> {

    /** 按主键查询并加行锁，用于召回等高风险事务防止重复提交。 */
    @Select("""
            SELECT id, batch_no, material_id, source_type, supplier_id, production_order_id,
                   quantity, remaining_quantity, status, production_date, expire_date,
                   warehouse_location, created_by, remark, created_at, updated_at
            FROM batch
            WHERE id = #{id}
            FOR UPDATE
            """)
    Batch selectByIdForUpdate(@Param("id") Long id);

    /**
     * 生产领料/出货共用的原子扣减模型：状态、余量、扣减与耗尽置位在同一条 SQL 中完成。
     */
    @Update("""
            UPDATE batch
            SET status = CASE
                    WHEN remaining_quantity = #{quantity} THEN 'DEPLETED'
                    ELSE status
                END,
                remaining_quantity = remaining_quantity - #{quantity}
            WHERE id = #{batchId}
              AND status = 'QUALIFIED'
              AND remaining_quantity >= #{quantity}
            """)
    int deductQualifiedStock(@Param("batchId") Long batchId, @Param("quantity") BigDecimal quantity);
}
