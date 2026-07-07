package com.xinghui.qualitytrace.dto.inspection;

import com.xinghui.qualitytrace.common.enums.InspectType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 检验项目响应体 —— 检验标准行 + 物料/工序名称装配，前端列表直接展示
 */
@Getter
@Builder
public class InspectionItemResponse {

    /** 检验项目ID */
    private final Long id;

    /** 项目编码 */
    private final String itemCode;

    /** 项目名称 */
    private final String itemName;

    /** 检验类型 */
    private final InspectType inspectType;

    /** 适用物料ID */
    private final Long materialId;

    /** 适用物料编码 */
    private final String materialCode;

    /** 适用物料名称 */
    private final String materialName;

    /** 挂靠工序ID（仅 IPQC） */
    private final Long processDefId;

    /** 挂靠工序编码 */
    private final String processCode;

    /** 挂靠工序名称 */
    private final String processName;

    /** 是否定量：1=定量 0=定性 */
    private final Integer isQuantitative;

    /** 检验标准描述 */
    private final String standardDesc;

    /** 下限 */
    private final BigDecimal lowerLimit;

    /** 上限 */
    private final BigDecimal upperLimit;

    /** 计量单位 */
    private final String unit;

    /** 创建时间 */
    private final LocalDateTime createdAt;
}
