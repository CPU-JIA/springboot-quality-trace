package com.xinghui.qualitytrace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinghui.qualitytrace.common.enums.InspectType;
import com.xinghui.qualitytrace.entity.InspectionItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 检验项目 Mapper —— 纯单表 CRUD */
@Mapper
public interface InspectionItemMapper extends BaseMapper<InspectionItem> {

    /**
     * 按物料、检验类型、挂靠工序统计标准项数量。
     *
     * <p>IQC/FQC 的 processDefId 传 null，要求标准项不挂工序；IPQC 传具体工序。
     * 该查询用于在生成检验任务前做配置完整性预检，避免产生无法提交的空标准任务。</p>
     */
    @Select("""
            SELECT COUNT(*)
            FROM inspection_item
            WHERE material_id = #{materialId}
              AND inspect_type = #{inspectType}
              AND (
                  (#{processDefId} IS NULL AND process_def_id IS NULL)
                  OR process_def_id = #{processDefId}
              )
            """)
    Long countStandards(@Param("materialId") Long materialId,
                        @Param("inspectType") InspectType inspectType,
                        @Param("processDefId") Long processDefId);

    /**
     * 统计某套检验标准当前关联的待检/检验中任务数量。
     *
     * <p>检验任务提交时会按当前 inspection_item 拉取应检项目与判限。若任务尚未完成时修改
     * 同一物料/类型/工序的标准，会导致任务突然多项、少项或判定口径变化，因此维护标准前必须拒绝。</p>
     */
    @Select("""
            SELECT COUNT(*)
            FROM inspection_task t
                     LEFT JOIN batch b ON b.id = t.batch_id
                     LEFT JOIN process_record pr ON pr.id = t.process_record_id
                     LEFT JOIN production_order po ON po.id = pr.production_order_id
            WHERE t.status <> 'COMPLETED'
              AND t.inspect_type = #{inspectType}
              AND (
                  (#{processDefId} IS NULL
                      AND t.batch_id IS NOT NULL
                      AND b.material_id = #{materialId})
                  OR
                  (#{processDefId} IS NOT NULL
                      AND t.process_record_id IS NOT NULL
                      AND po.material_id = #{materialId}
                      AND pr.process_def_id = #{processDefId})
              )
            """)
    Long countOpenTaskReferences(@Param("materialId") Long materialId,
                                 @Param("inspectType") InspectType inspectType,
                                 @Param("processDefId") Long processDefId);
}
