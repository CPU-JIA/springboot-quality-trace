package com.xinghui.qualitytrace.dto.production;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 工单 BOM 需求响应体 —— 展示计划需求、已领数量与缺口，供前端判断开工/入库条件。
 */
@Getter
@Builder
public class MaterialRequirementResponse {

    /** BOM 行ID */
    private final Long bomId;

    /** 子项物料ID */
    private final Long materialId;

    /** 子项物料编码 */
    private final String materialCode;

    /** 子项物料名称 */
    private final String materialName;

    /** 单位用量 */
    private final BigDecimal unitQuantity;

    /** 按工单计划数量计算的需求量 */
    private final BigDecimal requiredQuantity;

    /** 当前已领数量 */
    private final BigDecimal issuedQuantity;

    /** 当前缺口数量；已超领时为 0 */
    private final BigDecimal missingQuantity;

    /** 是否已满足计划数量需求 */
    private final boolean issuedEnough;
}
