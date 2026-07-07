package com.xinghui.qualitytrace.dto.production;

import com.xinghui.qualitytrace.entity.Batch;
import com.xinghui.qualitytrace.entity.ProductionOrder;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 生产工单详情响应体 —— 工单主信息 + 工序快照 + 领料消耗 + 产出批次
 */
@Getter
@Builder
public class ProductionOrderDetailResponse {

    /** 工单主信息 */
    private final ProductionOrder order;

    /** 生产物料编码 */
    private final String materialCode;

    /** 生产物料名称 */
    private final String materialName;

    /** 工序执行快照 */
    private final List<ProcessRecordResponse> processRecords;

    /** BOM 需求与领料达成情况 */
    private final List<MaterialRequirementResponse> materialRequirements;

    /** 领料消耗记录 */
    private final List<ConsumptionResponse> consumptions;

    /** 产出批次；未完工入库时为 null */
    private final Batch outputBatch;
}
