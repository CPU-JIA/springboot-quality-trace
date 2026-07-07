package com.xinghui.qualitytrace.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 缺陷类型枚举 —— 帕累托分析的分类维度（docs/02 §6.3 v_defect_pareto 视图）
 */
@Getter
@RequiredArgsConstructor
public enum DefectType {

    /** 外观缺陷：划痕/色差/毛边等 */
    APPEARANCE("外观"),

    /** 尺寸缺陷：超出图纸公差 */
    DIMENSION("尺寸"),

    /** 功能缺陷：性能参数不达标/功能失效 */
    FUNCTION("功能"),

    /** 安全缺陷：触电/起火/机械伤害等隐患（最高处置优先级） */
    SAFETY("安全"),

    /** 其他：材料物性等不归前四类的缺陷 */
    OTHER("其他");

    private final String label;
}
