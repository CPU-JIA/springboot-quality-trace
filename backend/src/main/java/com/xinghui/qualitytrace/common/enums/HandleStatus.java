package com.xinghui.qualitytrace.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 缺陷处置状态枚举
 */
@Getter
@RequiredArgsConstructor
public enum HandleStatus {

    /** 待处置：等待质量主管评审 */
    PENDING("待处置"),

    /** 已处置：处置方式已执行、批次状态已相应流转 */
    COMPLETED("已处置");

    private final String label;
}
