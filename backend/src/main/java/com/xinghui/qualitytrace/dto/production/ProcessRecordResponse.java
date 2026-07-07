package com.xinghui.qualitytrace.dto.production;

import com.xinghui.qualitytrace.common.enums.Conclusion;
import com.xinghui.qualitytrace.common.enums.ProcessStatus;
import com.xinghui.qualitytrace.common.enums.TaskStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 工序执行记录响应体 —— 工序记录 + 工序字典名称装配
 */
@Getter
@Builder
public class ProcessRecordResponse {

    /** 工序记录ID */
    private final Long id;

    /** 工单ID */
    private final Long productionOrderId;

    /** 工序ID */
    private final Long processDefId;

    /** 工序编码 */
    private final String processCode;

    /** 工序名称 */
    private final String processName;

    /** 是否 IPQC 检验点 */
    private final Integer needIpqc;

    /** 步骤序号 */
    private final Integer stepNo;

    /** 执行状态 */
    private final ProcessStatus status;

    /** 最新 IPQC 任务状态；非 IPQC 工序为空 */
    private final TaskStatus ipqcTaskStatus;

    /** 最新 IPQC 检验结论；未提交或非 IPQC 工序为空 */
    private final Conclusion ipqcConclusion;

    /** IPQC 是否已经合格/让步放行 */
    private final boolean ipqcReleased;

    /** 操作员ID */
    private final Long operatorId;

    /** 开工时间 */
    private final LocalDateTime startTime;

    /** 完工时间 */
    private final LocalDateTime endTime;

    /** 备注 */
    private final String remark;
}
