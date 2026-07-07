package com.xinghui.qualitytrace.dto.batch;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 原材料入库请求体 —— 仓库登记采购批次（F3-1）
 */
@Data
public class PurchaseInboundRequest {

    /** 原材料物料ID（必须为 RAW 且启用） */
    @NotNull(message = "物料不能为空")
    private Long materialId;

    /** 供应商ID（必须为合作中） */
    @NotNull(message = "供应商不能为空")
    private Long supplierId;

    /** 入库数量 */
    @NotNull(message = "入库数量不能为空")
    @DecimalMin(value = "0.001", message = "入库数量必须为正数")
    private BigDecimal quantity;

    /** 入库/生产日期（采购批次以入库日期作为生产日期口径） */
    @NotNull(message = "入库日期不能为空")
    private LocalDate productionDate;

    /** 仓库库位 */
    private String warehouseLocation;

    /** 备注 */
    private String remark;
}
