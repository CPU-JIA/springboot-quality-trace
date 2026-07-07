package com.xinghui.qualitytrace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xinghui.qualitytrace.common.enums.RecoveryStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 召回明细实体 —— 对应表 recall_detail（弱实体，依附召回单）
 *
 * <p>已出货部分关联 shipment；在库部分 shipmentId 为空，表示该批次仍在库需就地隔离。</p>
 */
@Data
@TableName("recall_detail")
public class RecallDetail {

    /** 召回明细ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属召回单ID */
    private Long recallOrderId;

    /** 受影响批次ID */
    private Long affectedBatchId;

    /** 关联出货记录ID（在库部分为空） */
    private Long shipmentId;

    /** 回收状态 */
    private RecoveryStatus recoveryStatus;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
