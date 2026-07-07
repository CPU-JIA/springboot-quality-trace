package com.xinghui.qualitytrace.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 召回单状态枚举
 *
 * <p>口径申报（经审计确认）：发起召回的事务内直接置 IN_PROGRESS（发起即执行），
 * CREATED 仅为字典完备态保留，正常业务流不停留于此。</p>
 */
@Getter
@RequiredArgsConstructor
public enum RecallStatus {

    /** 已创建（字典完备态，正常流程不停留） */
    CREATED("已创建"),

    /** 执行中：明细逐条跟踪回收 */
    IN_PROGRESS("执行中"),

    /** 已完成：全部明细达到终态（已回收/无法回收）后关闭 */
    COMPLETED("已完成");

    private final String label;
}
