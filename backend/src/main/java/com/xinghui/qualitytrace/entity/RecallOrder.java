package com.xinghui.qualitytrace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xinghui.qualitytrace.common.enums.RecallLevel;
import com.xinghui.qualitytrace.common.enums.RecallStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 召回单实体 —— 对应表 recall_order（质量事件的处置载体）
 */
@Data
@TableName("recall_order")
public class RecallOrder {

    /** 召回单ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 召回单号（RC-yyyyMMdd-3位序号） */
    private String recallNo;

    /** 问题源头批次ID */
    private Long sourceBatchId;

    /** 召回级别 */
    private RecallLevel recallLevel;

    /** 召回原因 */
    private String reason;

    /** 召回状态 */
    private RecallStatus status;

    /** 发起人ID */
    private Long initiatorId;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
