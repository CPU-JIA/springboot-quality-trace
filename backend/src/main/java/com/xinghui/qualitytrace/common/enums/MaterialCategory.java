package com.xinghui.qualitytrace.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 物料类别枚举 —— 原材料/半成品/成品统一建模的类别区分（docs/02 §4.6）
 *
 * <p>类别决定批次号前缀（{@link #batchPrefix}）与业务规则：
 * RAW 只能采购入库（IQC）；SEMI/PRODUCT 只能生产完工产出（FQC）；仅 PRODUCT 可出货。</p>
 */
@Getter
@RequiredArgsConstructor
public enum MaterialCategory {

    /** 原材料：外购，批次号前缀 RM（Raw Material） */
    RAW("原材料", "RM"),

    /** 半成品：自制中间件，批次号前缀 SF（Semi-Finished） */
    SEMI("半成品", "SF"),

    /** 成品：可出货销售，批次号前缀 FP（Finished Product） */
    PRODUCT("成品", "FP");

    private final String label;

    /** 该类别物料的批次号前缀（批次号规则：前缀-yyyyMMdd-3位序号） */
    private final String batchPrefix;
}
