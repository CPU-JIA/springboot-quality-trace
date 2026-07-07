package com.xinghui.qualitytrace.dto.trace;

import com.xinghui.qualitytrace.common.enums.BatchStatus;
import com.xinghui.qualitytrace.common.enums.MaterialCategory;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 正向追溯行 —— 给定批次，逐级向下游展开产出批次及出货流向
 */
@Data
public class TraceDownstreamRow {

    private Long batchId;
    private String batchNo;
    private String materialCode;
    private String materialName;
    private MaterialCategory materialCategory;
    private BatchStatus batchStatus;
    private BigDecimal remainingQuantity;
    private Integer level;
    private String path;
    private String shipmentNo;
    private Long shipmentId;
    private String customerName;
    private BigDecimal shippedQuantity;
    private LocalDate shipDate;
}
