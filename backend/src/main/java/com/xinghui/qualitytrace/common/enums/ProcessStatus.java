package com.xinghui.qualitytrace.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 工序执行状态枚举
 *
 * <p>常规流转：PENDING → IN_PROGRESS（开工）→ COMPLETED（报完工）。
 * 特殊回退：COMPLETED → IN_PROGRESS —— IPQC 结论不合格时工序退回返修
 * （docs/02 §5.7 异常闭环第 1 条），返修完成后重新报完工并生成新 IPQC 单复检。</p>
 */
@Getter
@RequiredArgsConstructor
public enum ProcessStatus {

    /** 待开工 */
    PENDING("待开工"),

    /** 进行中（含 IPQC 不合格退回返修的状态） */
    IN_PROGRESS("进行中"),

    /** 已完工（若为 IPQC 检验点，须其 IPQC 通过后下道工序方可开工） */
    COMPLETED("已完工");

    private final String label;
}
