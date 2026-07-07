package com.xinghui.qualitytrace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinghui.qualitytrace.entity.BatchConsumption;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/** 批次消耗 Mapper —— 单表 CRUD + 领料累加写入 */
@Mapper
public interface BatchConsumptionMapper extends BaseMapper<BatchConsumption> {

    /**
     * 同一工单多次领同一批次时累加数量，避免重复边污染追溯图。
     */
    @Insert("""
            INSERT INTO batch_consumption (production_order_id, consumed_batch_id, quantity)
            VALUES (#{orderId}, #{batchId}, #{quantity})
            ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity)
            """)
    int insertOrIncrease(@Param("orderId") Long orderId,
                         @Param("batchId") Long batchId,
                         @Param("quantity") BigDecimal quantity);
}
