package com.xinghui.qualitytrace.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 批次来源类型枚举 —— 与供应商/工单外键的互斥性由数据库 CHECK 强制
 * （chk_batch_source_fk：采购批必有供应商且无工单；生产批必有工单且无供应商）
 */
@Getter
@RequiredArgsConstructor
public enum SourceType {

    /** 采购入库：原材料批次，关联供应商 */
    PURCHASE("采购入库"),

    /** 生产完工：半成品/成品批次，关联产出工单（1:1） */
    PRODUCTION("生产完工");

    private final String label;
}
