package com.xinghui.qualitytrace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinghui.qualitytrace.common.enums.OrderStatus;
import com.xinghui.qualitytrace.dto.production.ProductionOrderResponse;
import com.xinghui.qualitytrace.entity.ProductionOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 生产工单 Mapper —— 单表 CRUD + 高风险事务辅助查询 */
@Mapper
public interface ProductionOrderMapper extends BaseMapper<ProductionOrder> {

    /** 分页查询工单列表，并带出生产物料编码/名称，避免前端为显示列表全量加载物料。 */
    @Select("""
            <script>
            SELECT po.id,
                   po.order_no,
                   po.material_id,
                   m.material_code,
                   m.name AS material_name,
                   m.category AS material_category,
                   po.plan_quantity,
                   po.actual_quantity,
                   po.status,
                   po.plan_start_date,
                   po.plan_end_date,
                   po.actual_start_time,
                   po.actual_end_time,
                   po.manager_id,
                   po.remark,
                   po.created_at,
                   po.updated_at
            FROM production_order po
                     JOIN material m ON m.id = po.material_id
            WHERE 1 = 1
            <if test="keyword != null and keyword != ''">
              AND (
                  po.order_no LIKE CONCAT('%', #{keyword}, '%')
                  OR po.remark LIKE CONCAT('%', #{keyword}, '%')
                  OR m.material_code LIKE CONCAT('%', #{keyword}, '%')
                  OR m.name LIKE CONCAT('%', #{keyword}, '%')
              )
            </if>
            <if test="status != null">
              AND po.status = #{status}
            </if>
            <if test="materialId != null">
              AND po.material_id = #{materialId}
            </if>
            ORDER BY po.created_at DESC, po.id DESC
            </script>
            """)
    Page<ProductionOrderResponse> selectPageWithMaterial(Page<ProductionOrderResponse> page,
                                                         @Param("keyword") String keyword,
                                                         @Param("status") OrderStatus status,
                                                         @Param("materialId") Long materialId);

    /** 按主键查询并加行锁，用于生产领料/工序流转/完工入库串行化同一工单。 */
    @Select("""
            SELECT id, order_no, material_id, plan_quantity, actual_quantity, status,
                   plan_start_date, plan_end_date, actual_start_time, actual_end_time,
                   manager_id, remark, created_at, updated_at
            FROM production_order
            WHERE id = #{id}
            FOR UPDATE
            """)
    ProductionOrder selectByIdForUpdate(@Param("id") Long id);
}
