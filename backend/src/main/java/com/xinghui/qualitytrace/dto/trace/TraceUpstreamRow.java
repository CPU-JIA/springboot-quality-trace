package com.xinghui.qualitytrace.dto.trace;

import com.xinghui.qualitytrace.common.enums.MaterialCategory;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 反向追溯行 —— 给定批次，逐级向上游展开其消耗来源
 */
@Data
public class TraceUpstreamRow {

    private Long batchId;
    private String batchNo;
    private String materialCode;
    private String materialName;
    private MaterialCategory materialCategory;
    private String supplierName;
    private String productionOrderNo;
    private String processSummary;
    private String inspectionSummary;
    private BigDecimal consumedQuantity;
    private Integer level;
    private String path;
}
