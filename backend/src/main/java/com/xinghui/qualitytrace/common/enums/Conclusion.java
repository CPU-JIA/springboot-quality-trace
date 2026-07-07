package com.xinghui.qualitytrace.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 检验结论枚举
 *
 * <p>结论联动（docs/02 §5.6/§5.7）：
 * <ul>
 *   <li>QUALIFIED / CONCESSION → 批次流转为合格在库（CONCESSION 强制同步登记 MINOR 缺陷留痕）；</li>
 *   <li>UNQUALIFIED → IQC/FQC 冻结批次（应用层 + 触发器双防线）；IPQC 工序退回返修。</li>
 * </ul>
 * 让步双路径边界（§5.7 第 3 条）：轻微偏差检验员可直接判 CONCESSION（须登记缺陷由主管
 * 评审留痕）；严重偏差必须判 UNQUALIFIED 走冻结评审。</p>
 */
@Getter
@RequiredArgsConstructor
public enum Conclusion {

    /** 合格 */
    QUALIFIED("合格"),

    /** 不合格：触发批次冻结 / 工序返修 */
    UNQUALIFIED("不合格"),

    /** 让步接收：轻微偏差不影响使用，放行但登记缺陷留痕 */
    CONCESSION("让步接收");

    private final String label;
}
