package com.xinghui.qualitytrace.dto.batch;

import com.xinghui.qualitytrace.entity.Batch;
import com.xinghui.qualitytrace.entity.BatchOverview;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 批次详情响应体 —— 批次主信息 + 台账投影 + 检验任务历史
 */
@Getter
@Builder
public class BatchDetailResponse {

    /** 批次原始实体（含外键ID、余量、状态等完整字段） */
    private final Batch batch;

    /** 批次全景投影（含物料/供应商/工单/登记人名称） */
    private final BatchOverview overview;

    /** 该批次关联的检验任务历史 */
    private final List<InspectionTaskBrief> inspectionTasks;

    /** 该批次关联的缺陷记录 */
    private final List<BatchDefectBrief> defects;

    /** 该批次被生产工单领用的消耗去向 */
    private final List<BatchConsumptionBrief> consumptions;

    /** 该批次作为成品出货的客户流向 */
    private final List<BatchShipmentBrief> shipments;
}
