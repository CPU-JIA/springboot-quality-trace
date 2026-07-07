package com.xinghui.qualitytrace.dto.defect;

import com.xinghui.qualitytrace.common.enums.DefectType;
import com.xinghui.qualitytrace.common.enums.HandleMethod;
import com.xinghui.qualitytrace.common.enums.HandleStatus;
import com.xinghui.qualitytrace.common.enums.Severity;
import com.xinghui.qualitytrace.common.enums.BatchStatus;
import com.xinghui.qualitytrace.common.enums.SourceType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 缺陷响应体 —— 缺陷主字段 + 批次/工序/检验单编号装配
 */
@Getter
@Builder
public class DefectResponse {

    private final Long id;
    private final String defectNo;
    private final Long batchId;
    private final String batchNo;
    private final BatchStatus batchStatus;
    private final SourceType batchSourceType;
    private final Long processRecordId;
    private final Integer stepNo;
    private final String processName;
    private final Long inspectionTaskId;
    private final String taskNo;
    private final DefectType defectType;
    private final Severity severity;
    private final BigDecimal quantity;
    private final String description;
    private final HandleMethod handleMethod;
    private final HandleStatus handleStatus;
    private final Long handlerId;
    private final LocalDateTime handledAt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
