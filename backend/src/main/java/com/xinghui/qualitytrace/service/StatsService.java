package com.xinghui.qualitytrace.service;

import com.xinghui.qualitytrace.dto.stats.DashboardStatsResponse;
import com.xinghui.qualitytrace.dto.stats.DefectParetoRow;
import com.xinghui.qualitytrace.dto.stats.PassRateTrendRow;
import com.xinghui.qualitytrace.dto.stats.SupplierQualityRow;
import com.xinghui.qualitytrace.mapper.StatsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 统计服务 —— 质量看板与报表查询（F8）
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final StatsMapper statsMapper;

    /** 首页质量总览卡片。 */
    public DashboardStatsResponse dashboard() {
        BigDecimal monthPassRate = statsMapper.monthPassRate();
        return DashboardStatsResponse.builder()
                .activeInspectionTasks(statsMapper.countActiveInspectionTasks())
                .qualifiedBatchCount(statsMapper.countQualifiedBatches())
                .activeRecallCount(statsMapper.countActiveRecalls())
                .monthPassRate(monthPassRate == null ? BigDecimal.ZERO : monthPassRate)
                .build();
    }

    /** 最近 months 个月的检验合格率趋势。 */
    public List<PassRateTrendRow> passRateTrend(int months) {
        int safeMonths = months <= 0 ? 6 : Math.min(months, 24);
        LocalDate startMonth = LocalDate.now().withDayOfMonth(1).minusMonths(safeMonths - 1L);
        return statsMapper.passRateTrend(startMonth);
    }

    /** 缺陷帕累托。 */
    public List<DefectParetoRow> defectPareto() {
        return statsMapper.defectPareto();
    }

    /** 供应商质量排名。 */
    public List<SupplierQualityRow> supplierQuality() {
        return statsMapper.supplierQuality();
    }
}
