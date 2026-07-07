package com.xinghui.qualitytrace.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 缺陷处置方式枚举 —— 质量主管评审冻结批次后的四种去向（docs/02 §5.6 FROZEN 出边）
 */
@Getter
@RequiredArgsConstructor
public enum HandleMethod {

    /** 返工：批次回待检，处置完成时自动生成同类型重检任务（§5.7 第 2 条，返工不换料） */
    REWORK("返工"),

    /** 报废：批次归档为 SCRAPPED */
    SCRAP("报废"),

    /** 让步放行：评审后批次流转为合格在库 */
    CONCESSION("让步放行"),

    /** 退货：原料批次退回供应商，归档为 RETURNED */
    RETURN("退货");

    private final String label;
}
