package com.xinghui.qualitytrace.dto.batch;

import com.xinghui.qualitytrace.common.enums.Conclusion;
import com.xinghui.qualitytrace.common.enums.InspectType;
import com.xinghui.qualitytrace.common.enums.TaskStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 批次详情页中的检验任务摘要 —— 当前 A3 先用于展示 IQC/FQC 任务历史
 */
@Getter
@Builder
public class InspectionTaskBrief {

    /** 检验任务ID */
    private final Long id;

    /** 检验单号 */
    private final String taskNo;

    /** 检验类型 */
    private final InspectType inspectType;

    /** 任务状态 */
    private final TaskStatus status;

    /** 检验结论 */
    private final Conclusion conclusion;

    /** 检验员ID */
    private final Long inspectorId;

    /** 领取时间 */
    private final LocalDateTime assignedAt;

    /** 完成时间 */
    private final LocalDateTime completedAt;

    /** 创建时间 */
    private final LocalDateTime createdAt;
}
