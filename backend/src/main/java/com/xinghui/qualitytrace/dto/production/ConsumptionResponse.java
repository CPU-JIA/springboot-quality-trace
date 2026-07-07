package com.xinghui.qualitytrace.dto.production;

import com.xinghui.qualitytrace.common.enums.BatchStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工单领料消耗响应体 —— 追溯边 + 被消耗批次/物料信息
 */
@Getter
@Builder
public class ConsumptionResponse {

    /** 消耗记录ID */
    private final Long id;

    /** 被消耗批次ID */
    private final Long consumedBatchId;

    /** 被消耗批次号 */
    private final String batchNo;

    /** 物料编码 */
    private final String materialCode;

    /** 物料名称 */
    private final String materialName;

    /** 消耗数量 */
    private final BigDecimal quantity;

    /** 当前批次状态 */
    private final BatchStatus batchStatus;

    /** 领料时间 */
    private final LocalDateTime createdAt;
}
