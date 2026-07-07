package com.xinghui.qualitytrace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinghui.qualitytrace.entity.Bom;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** BOM Mapper —— 纯单表 CRUD（构成树组装与 DFS 防环在 Service 层内存完成） */
@Mapper
public interface BomMapper extends BaseMapper<Bom> {

    /**
     * 统计某父项物料当前未关闭生产工单数量。
     *
     * <p>本系统的工单领料需求按当前 BOM 计算。若存在未关闭工单，修改该父项 BOM 会改变
     * 在制工单的领料/完工校验口径，因此必须拒绝。</p>
     */
    @Select("""
            SELECT COUNT(*)
            FROM production_order
            WHERE material_id = #{parentMaterialId}
              AND status <> 'CLOSED'
            """)
    Long countOpenOrderReferences(@Param("parentMaterialId") Long parentMaterialId);
}
