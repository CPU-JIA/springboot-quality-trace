package com.xinghui.qualitytrace.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 检验单任务状态枚举
 *
 * <p>流转：PENDING（业务动作自动生成）→ IN_PROGRESS（检验员领取）→ COMPLETED（提交结论）。
 * 数据库 CHECK 约束保证 COMPLETED 必有结论（chk_task_complete）。</p>
 */
@Getter
@RequiredArgsConstructor
public enum TaskStatus {

    /** 待领取：任务池中等待检验员认领 */
    PENDING("待领取"),

    /** 检验中：已被领取，逐项录入中 */
    IN_PROGRESS("检验中"),

    /** 已完成：结论已提交（合格/不合格/让步接收） */
    COMPLETED("已完成");

    private final String label;
}
