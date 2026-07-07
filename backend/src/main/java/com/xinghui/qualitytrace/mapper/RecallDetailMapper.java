package com.xinghui.qualitytrace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinghui.qualitytrace.entity.RecallDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 召回明细 Mapper —— 单表 CRUD */
@Mapper
public interface RecallDetailMapper extends BaseMapper<RecallDetail> {

    /** 按主键查询并加行锁，用于明细状态更新时防止并发覆盖终态。 */
    @Select("""
            SELECT id, recall_order_id, affected_batch_id, shipment_id,
                   recovery_status, remark, created_at, updated_at
            FROM recall_detail
            WHERE id = #{id}
            FOR UPDATE
            """)
    RecallDetail selectByIdForUpdate(@Param("id") Long id);

    /** 按召回单查询明细并加行锁，用于关闭召回单前冻结明细状态快照。 */
    @Select("""
            SELECT id, recall_order_id, affected_batch_id, shipment_id,
                   recovery_status, remark, created_at, updated_at
            FROM recall_detail
            WHERE recall_order_id = #{recallOrderId}
            ORDER BY id
            FOR UPDATE
            """)
    List<RecallDetail> selectByRecallOrderIdForUpdate(@Param("recallOrderId") Long recallOrderId);
}
