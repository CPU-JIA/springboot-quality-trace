package com.xinghui.qualitytrace.dto.shipment;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 出货响应体 —— 出货记录 + 批次/客户名称装配
 */
@Getter
@Builder
public class ShipmentResponse {

    private final Long id;
    private final String shipmentNo;
    private final Long batchId;
    private final String batchNo;
    private final Long customerId;
    private final String customerName;
    private final BigDecimal quantity;
    private final LocalDate shipDate;
    private final Long operatorId;
    private final String remark;
    private final LocalDateTime createdAt;
}
