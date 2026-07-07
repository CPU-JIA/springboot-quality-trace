package com.xinghui.qualitytrace.dto.batch;

import com.xinghui.qualitytrace.common.enums.DefectType;
import com.xinghui.qualitytrace.common.enums.HandleMethod;
import com.xinghui.qualitytrace.common.enums.HandleStatus;
import com.xinghui.qualitytrace.common.enums.Severity;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 批次详情中的缺陷摘要 —— 让台账页能直接看到质量异常与处置状态
 */
@Getter
@Builder
public class BatchDefectBrief {

    private final Long id;
    private final String defectNo;
    private final DefectType defectType;
    private final Severity severity;
    private final BigDecimal quantity;
    private final String description;
    private final HandleMethod handleMethod;
    private final HandleStatus handleStatus;
    private final LocalDateTime createdAt;
}
