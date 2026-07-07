package com.xinghui.qualitytrace.dto.inspection;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 检验记录明细响应体 —— 明细结果 + 检验项目名称装配
 */
@Getter
@Builder
public class InspectionRecordResponse {

    private final Long id;
    private final Long inspectionItemId;
    private final String itemCode;
    private final String itemName;
    private final BigDecimal measuredValue;
    private final String resultDesc;
    private final Integer isPass;
    private final LocalDateTime inspectedAt;
}
