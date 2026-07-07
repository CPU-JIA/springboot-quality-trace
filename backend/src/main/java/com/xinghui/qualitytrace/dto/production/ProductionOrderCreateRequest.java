package com.xinghui.qualitytrace.dto.production;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 生产工单创建请求体 —— 创建工单并按工艺路线生成工序快照（F4-1）
 */
@Data
public class ProductionOrderCreateRequest {

    /** 生产物料ID（必须为半成品或成品） */
    @NotNull(message = "生产物料不能为空")
    private Long materialId;

    /** 计划生产数量 */
    @NotNull(message = "计划数量不能为空")
    @DecimalMin(value = "0.001", message = "计划数量必须为正数")
    private BigDecimal planQuantity;

    /** 计划开始日期 */
    @NotNull(message = "计划开始日期不能为空")
    private LocalDate planStartDate;

    /** 计划结束日期 */
    @NotNull(message = "计划结束日期不能为空")
    private LocalDate planEndDate;

    /** 备注 */
    private String remark;
}
