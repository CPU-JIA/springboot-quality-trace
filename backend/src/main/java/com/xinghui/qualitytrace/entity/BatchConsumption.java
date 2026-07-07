package com.xinghui.qualitytrace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 批次消耗实体 —— 对应表 batch_consumption（追溯链的边）
 *
 * <p>语义：某生产工单消耗了某批次多少数量。双向追溯正是沿该边与产出批次递归遍历。</p>
 */
@Data
@TableName("batch_consumption")
public class BatchConsumption {

    /** 消耗记录ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 消耗方工单ID */
    private Long productionOrderId;

    /** 被消耗批次ID */
    private Long consumedBatchId;

    /** 消耗数量 */
    private BigDecimal quantity;

    /** 领料时间 */
    private LocalDateTime createdAt;
}
