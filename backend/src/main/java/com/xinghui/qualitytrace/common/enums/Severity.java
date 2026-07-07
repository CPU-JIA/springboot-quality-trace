package com.xinghui.qualitytrace.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 缺陷严重度枚举 —— 让步双路径的分界依据（docs/02 §5.7 第 3 条）：
 * MINOR 可由检验员直接判让步接收（登记缺陷留痕）；CRITICAL/MAJOR 必须走冻结评审。
 */
@Getter
@RequiredArgsConstructor
public enum Severity {

    /** 致命：危及人身安全或整批报废级 */
    CRITICAL("致命"),

    /** 严重：主要功能受损，需评审处置 */
    MAJOR("严重"),

    /** 轻微：不影响使用的微小偏差 */
    MINOR("轻微");

    private final String label;
}
