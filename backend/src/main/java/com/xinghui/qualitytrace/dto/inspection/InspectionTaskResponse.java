package com.xinghui.qualitytrace.dto.inspection;

import com.xinghui.qualitytrace.common.enums.Conclusion;
import com.xinghui.qualitytrace.common.enums.InspectType;
import com.xinghui.qualitytrace.common.enums.TaskStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 检验任务响应体 —— 任务主字段 + 批次/工序目标信息装配，供任务池直接展示
 */
@Getter
@Builder
public class InspectionTaskResponse {

    private final Long id;
    private final String taskNo;
    private final InspectType inspectType;
    private final Long batchId;
    private final String batchNo;
    private final Long processRecordId;
    private final Integer stepNo;
    private final String processName;
    private final TaskStatus status;
    private final Conclusion conclusion;
    private final Long inspectorId;
    private final LocalDateTime assignedAt;
    private final LocalDateTime completedAt;
    private final String remark;
    private final LocalDateTime createdAt;
}
