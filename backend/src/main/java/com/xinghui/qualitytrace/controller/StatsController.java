package com.xinghui.qualitytrace.controller;

import com.xinghui.qualitytrace.common.result.Result;
import com.xinghui.qualitytrace.dto.stats.DashboardStatsResponse;
import com.xinghui.qualitytrace.dto.stats.DefectParetoRow;
import com.xinghui.qualitytrace.dto.stats.PassRateTrendRow;
import com.xinghui.qualitytrace.dto.stats.SupplierQualityRow;
import com.xinghui.qualitytrace.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 统计报表接口（F8）—— 看板、趋势、帕累托与供应商质量排名
 */
@Tag(name = "17-统计报表", description = "质量统计看板与报表")
@Validated
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @Operation(summary = "质量总览看板")
    @GetMapping("/dashboard")
    public Result<DashboardStatsResponse> dashboard() {
        return Result.ok(statsService.dashboard());
    }

    @Operation(summary = "检验合格率趋势")
    @GetMapping("/pass-rate-trend")
    public Result<List<PassRateTrendRow>> passRateTrend(
            @RequestParam(defaultValue = "6")
            @Min(value = 1, message = "统计月份范围至少为1个月")
            @Max(value = 24, message = "统计月份范围不能超过24个月")
            int months) {
        return Result.ok(statsService.passRateTrend(months));
    }

    @Operation(summary = "缺陷帕累托")
    @GetMapping("/defect-pareto")
    public Result<List<DefectParetoRow>> defectPareto() {
        return Result.ok(statsService.defectPareto());
    }

    @Operation(summary = "供应商质量排名")
    @GetMapping("/supplier-quality")
    public Result<List<SupplierQualityRow>> supplierQuality() {
        return Result.ok(statsService.supplierQuality());
    }
}
