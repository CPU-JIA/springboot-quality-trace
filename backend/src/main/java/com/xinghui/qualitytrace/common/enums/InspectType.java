package com.xinghui.qualitytrace.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 检验类型枚举 —— 三级检验体系（docs/01 需求 §3.5）
 *
 * <p>检验对象的多态约定（数据库 CHECK 强制互斥，docs/02 §5.4）：
 * IQC/FQC 针对批次（batch_id 非空）；IPQC 针对工序执行记录（process_record_id 非空）。</p>
 */
@Getter
@RequiredArgsConstructor
public enum InspectType {

    /** 来料检验（Incoming Quality Control）：原材料入库时自动生成 */
    IQC("来料检验"),

    /** 过程检验（In-Process Quality Control）：IPQC 检验点工序报完工时自动生成 */
    IPQC("过程检验"),

    /** 成品检验（Final Quality Control）：完工入库/半成品入库时自动生成 */
    FQC("成品检验");

    private final String label;
}
