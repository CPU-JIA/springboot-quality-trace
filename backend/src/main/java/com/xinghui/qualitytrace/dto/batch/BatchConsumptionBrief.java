package com.xinghui.qualitytrace.dto.batch;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 批次详情中的消耗去向摘要 —— 展示该批次被哪些生产工单领用
 */
@Getter
@Builder
public class BatchConsumptionBrief {

    /** 消耗记录ID */
    private final Long id;

    /** 消耗方工单ID */
    private final Long productionOrderId;

    /** 消耗方工单号 */
    private final String productionOrderNo;

    /** 消耗数量 */
    private final BigDecimal quantity;

    /** 领料时间 */
    private final LocalDateTime createdAt;
}
