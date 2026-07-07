package com.xinghui.qualitytrace.dto.shipment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 成品出货请求体 —— 从合格成品批次出货给客户（F6-1）
 */
@Data
public class ShipmentCreateRequest {

    /** 出货批次ID */
    @NotNull(message = "出货批次不能为空")
    private Long batchId;

    /** 客户ID */
    @NotNull(message = "客户不能为空")
    private Long customerId;

    /** 出货数量 */
    @NotNull(message = "出货数量不能为空")
    @DecimalMin(value = "0.001", message = "出货数量必须为正数")
    private BigDecimal quantity;

    /** 出货日期 */
    @NotNull(message = "出货日期不能为空")
    private LocalDate shipDate;

    /** 备注 */
    private String remark;
}
