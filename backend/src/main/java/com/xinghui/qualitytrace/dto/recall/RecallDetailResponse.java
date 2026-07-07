package com.xinghui.qualitytrace.dto.recall;

import com.xinghui.qualitytrace.common.enums.RecoveryStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 召回明细响应体 —— 明细 + 受影响批次/出货流向装配
 */
@Getter
@Builder
public class RecallDetailResponse {

    private final Long id;
    private final Long recallOrderId;
    private final Long affectedBatchId;
    private final String affectedBatchNo;
    private final Long shipmentId;
    private final String shipmentNo;
    private final String customerName;
    private final RecoveryStatus recoveryStatus;
    private final String remark;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
