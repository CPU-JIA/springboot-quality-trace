package com.xinghui.qualitytrace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinghui.qualitytrace.entity.Shipment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 出货 Mapper —— 单表 CRUD + 列表关联查询。 */
@Mapper
public interface ShipmentMapper extends BaseMapper<Shipment> {

    /**
     * 出货分页查询。
     *
     * <p>关键字需要覆盖前端提示的"出货单号 / 批次号 / 客户"三类信息，因此这里用
     * JOIN 让数据库侧完成过滤，避免只查出货单主表造成搜索结果与用户预期不一致。</p>
     */
    @Select("""
            <script>
            SELECT sh.id, sh.shipment_no, sh.batch_id, sh.customer_id, sh.quantity,
                   sh.ship_date, sh.operator_id, sh.remark, sh.created_at
            FROM shipment sh
                     JOIN batch b ON b.id = sh.batch_id
                     JOIN customer c ON c.id = sh.customer_id
            <where>
                <if test="batchId != null">
                    AND sh.batch_id = #{batchId}
                </if>
                <if test="customerId != null">
                    AND sh.customer_id = #{customerId}
                </if>
                <if test="keyword != null and keyword != ''">
                    AND (
                        sh.shipment_no LIKE CONCAT('%', #{keyword}, '%')
                        OR sh.remark LIKE CONCAT('%', #{keyword}, '%')
                        OR b.batch_no LIKE CONCAT('%', #{keyword}, '%')
                        OR c.customer_code LIKE CONCAT('%', #{keyword}, '%')
                        OR c.name LIKE CONCAT('%', #{keyword}, '%')
                    )
                </if>
            </where>
            ORDER BY sh.created_at DESC, sh.id DESC
            </script>
            """)
    Page<Shipment> selectPageForList(Page<Shipment> page,
                                     @Param("batchId") Long batchId,
                                     @Param("customerId") Long customerId,
                                     @Param("keyword") String keyword);
}
