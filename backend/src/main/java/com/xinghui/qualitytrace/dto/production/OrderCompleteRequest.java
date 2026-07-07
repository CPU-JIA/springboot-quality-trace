package com.xinghui.qualitytrace.dto.production;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 工单完工入库请求体 —— 生成生产来源批次并自动创建 FQC 任务（F4-4）
 */
@Data
public class OrderCompleteRequest {

    /** 实际完工数量 */
    @NotNull(message = "实际完工数量不能为空")
    @DecimalMin(value = "0.001", message = "实际完工数量必须为正数")
    private BigDecimal actualQuantity;

    /** 完工/生产日期 */
    @NotNull(message = "生产日期不能为空")
    private LocalDate productionDate;

    /** 成品/半成品入库库位 */
    private String warehouseLocation;

    /** 备注 */
    private String remark;
}
