package com.xinghui.qualitytrace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinghui.qualitytrace.entity.Material;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 物料 Mapper —— 单表 CRUD + 停用前的在库批次校验查询
 */
@Mapper
public interface MaterialMapper extends BaseMapper<Material> {

    /** 按主键查询并加行锁，用于工单创建与 BOM 调整串行化同一父项物料。 */
    @Select("""
            SELECT id, material_code, name, category, spec, unit, shelf_life_days,
                   status, created_at, updated_at
            FROM material
            WHERE id = #{id}
            FOR UPDATE
            """)
    Material selectByIdForUpdate(@Param("id") Long id);

    /**
     * 统计某物料的"在库"批次数（停用物料前的业务校验）。
     * 在库口径：剩余量 > 0 且状态处于可流转态（待检/检验中/合格/冻结）——
     * 这些批次仍会发生业务动作，物料停用会使其流程悬空，故拒绝停用。
     *
     * @param materialId 物料ID
     * @return 在库批次数量
     */
    @Select("""
            SELECT COUNT(*)
            FROM batch
            WHERE material_id = #{materialId}
              AND remaining_quantity > 0
              AND status IN ('PENDING_INSPECT', 'INSPECTING', 'QUALIFIED', 'FROZEN')
            """)
    long countActiveBatches(@Param("materialId") Long materialId);
}
