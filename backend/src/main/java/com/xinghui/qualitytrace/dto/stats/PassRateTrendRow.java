package com.xinghui.qualitytrace.dto.stats;

import com.xinghui.qualitytrace.common.enums.InspectType;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 合格率趋势行 —— 按月份 + 检验类型聚合（F8-2）
 */
@Data
public class PassRateTrendRow {

    /** 月份（yyyy-MM） */
    private String month;

    /** 检验类型 */
    private InspectType inspectType;

    /** 已完成检验单数 */
    private Long total;

    /** 合格单数（含让步接收） */
    private Long pass;

    /** 合格率百分比 */
    private BigDecimal passRate;
}
