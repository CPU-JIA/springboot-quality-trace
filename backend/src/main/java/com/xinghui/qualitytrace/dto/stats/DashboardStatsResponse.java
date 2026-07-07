package com.xinghui.qualitytrace.dto.stats;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 质量看板指标响应体 —— 首页核心指标卡片（F8-1）
 */
@Getter
@Builder
public class DashboardStatsResponse {

    /** 待领取/检验中的任务数 */
    private final Long activeInspectionTasks;

    /** 合格在库批次数 */
    private final Long qualifiedBatchCount;

    /** 进行中召回单数 */
    private final Long activeRecallCount;

    /** 本月检验合格率（合格+让步 / 已完成检验单，百分比） */
    private final BigDecimal monthPassRate;
}
