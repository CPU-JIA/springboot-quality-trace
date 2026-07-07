package com.xinghui.qualitytrace.dto.stats;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 供应商质量排名行 —— 来源于 v_supplier_quality 视图（F8-4）
 */
@Data
public class SupplierQualityRow {

    private Long supplierId;
    private String supplierCode;
    private String supplierName;
    private Long batchTotal;
    private Long iqcTotal;
    private Long iqcPass;
    private BigDecimal passRate;
    private Long defectCount;
    private Long recallCount;
}
