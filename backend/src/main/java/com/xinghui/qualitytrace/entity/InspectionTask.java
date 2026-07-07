package com.xinghui.qualitytrace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xinghui.qualitytrace.common.enums.Conclusion;
import com.xinghui.qualitytrace.common.enums.InspectType;
import com.xinghui.qualitytrace.common.enums.TaskStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 检验任务实体 —— 对应表 inspection_task（IQC/IPQC/FQC 统一任务载体）
 *
 * <p>当前 A3 先用于"原材料入库自动生成 IQC 任务"；后续 A5 会继续扩展领取、
 * 提交明细、结论联动与缺陷登记等完整闭环。</p>
 */
@Data
@TableName("inspection_task")
public class InspectionTask {

    /** 检验单ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 检验单号（QC-yyyyMMdd-3位序号） */
    private String taskNo;

    /** 检验类型 */
    private InspectType inspectType;

    /** 受检批次ID（IQC/FQC 必填） */
    private Long batchId;

    /** 受检工序执行ID（IPQC 必填） */
    private Long processRecordId;

    /** 任务状态 */
    private TaskStatus status;

    /** 检验结论（完成时必填） */
    private Conclusion conclusion;

    /** 检验员ID（领取时记录） */
    private Long inspectorId;

    /** 领取时间 */
    private LocalDateTime assignedAt;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
