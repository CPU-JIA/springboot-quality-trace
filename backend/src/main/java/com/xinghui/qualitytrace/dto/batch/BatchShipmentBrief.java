package com.xinghui.qualitytrace.dto.batch;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 批次详情中的出货流向摘要 —— 展示成品批次流向了哪些客户
 */
@Getter
@Builder
public class BatchShipmentBrief {

    private final Long id;
    private final String shipmentNo;
    private final Long customerId;
    private final String customerName;
    private final BigDecimal quantity;
    private final LocalDate shipDate;
    private final String remark;
    private final LocalDateTime createdAt;
}
