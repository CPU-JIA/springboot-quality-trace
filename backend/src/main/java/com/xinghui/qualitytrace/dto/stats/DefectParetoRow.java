package com.xinghui.qualitytrace.dto.stats;

import com.xinghui.qualitytrace.common.enums.DefectType;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 缺陷帕累托行 —— 来源于 v_defect_pareto 视图（F8-3）
 */
@Data
public class DefectParetoRow {

    /** 缺陷类型 */
    private DefectType defectType;

    /** 缺陷记录条数（帕累托主指标） */
    private Long recordCount;

    /** 缺陷数量合计（仅同单位钻取参考） */
    private BigDecimal defectQuantity;

    /** 条数占比 */
    private BigDecimal recordRatio;

    /** 累计占比 */
    private BigDecimal cumulativeRatio;
}
