package com.xinghui.qualitytrace.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 召回级别枚举 —— 参照《缺陷消费品召回管理规定》的分级惯例
 */
@Getter
@RequiredArgsConstructor
public enum RecallLevel {

    /** 一级：已经或可能造成严重健康危害（如干烧起火），最高优先级 */
    I("一级召回"),

    /** 二级：可能造成暂时性健康危害 */
    II("二级召回"),

    /** 三级：一般不会造成危害，但违反安全标准 */
    III("三级召回");

    private final String label;
}
