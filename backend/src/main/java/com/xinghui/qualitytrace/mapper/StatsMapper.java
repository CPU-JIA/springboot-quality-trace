package com.xinghui.qualitytrace.mapper;

import com.xinghui.qualitytrace.dto.stats.DefectParetoRow;
import com.xinghui.qualitytrace.dto.stats.PassRateTrendRow;
import com.xinghui.qualitytrace.dto.stats.SupplierQualityRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 统计 Mapper —— 报表类查询集中在此，SQL 口径清晰可审计
 */
@Mapper
public interface StatsMapper {

    /** 待处理检验任务数（待领取 + 检验中）。 */
    @Select("""
            SELECT COUNT(*)
            FROM inspection_task
            WHERE status IN ('PENDING', 'IN_PROGRESS')
            """)
    Long countActiveInspectionTasks();

    /** 合格在库批次数。 */
    @Select("""
            SELECT COUNT(*)
            FROM batch
            WHERE status = 'QUALIFIED'
              AND remaining_quantity > 0
            """)
    Long countQualifiedBatches();

    /** 进行中召回单数。 */
    @Select("""
            SELECT COUNT(*)
            FROM recall_order
            WHERE status = 'IN_PROGRESS'
            """)
    Long countActiveRecalls();

    /** 本月检验合格率（合格 + 让步 / 已完成）。 */
    @Select("""
            SELECT ROUND(
                COUNT(CASE WHEN conclusion IN ('QUALIFIED', 'CONCESSION') THEN 1 END)
                / NULLIF(COUNT(*), 0) * 100, 2)
            FROM inspection_task
            WHERE status = 'COMPLETED'
              AND completed_at >= DATE_FORMAT(CURDATE(), '%Y-%m-01')
            """)
    BigDecimal monthPassRate();

    /** 合格率趋势：按本月在内的最近 N 个自然月和检验类型聚合。 */
    @Select("""
            SELECT DATE_FORMAT(completed_at, '%Y-%m') AS month,
                   inspect_type,
                   COUNT(*) AS total,
                   COUNT(CASE WHEN conclusion IN ('QUALIFIED', 'CONCESSION') THEN 1 END) AS pass,
                   ROUND(COUNT(CASE WHEN conclusion IN ('QUALIFIED', 'CONCESSION') THEN 1 END)
                         / NULLIF(COUNT(*), 0) * 100, 2) AS pass_rate
            FROM inspection_task
            WHERE status = 'COMPLETED'
              AND completed_at >= #{startMonth}
            GROUP BY DATE_FORMAT(completed_at, '%Y-%m'), inspect_type
            ORDER BY month, inspect_type
            """)
    List<PassRateTrendRow> passRateTrend(@Param("startMonth") LocalDate startMonth);

    /** 缺陷帕累托：直接读取数据库视图，保持统计口径唯一。 */
    @Select("""
            SELECT defect_type,
                   record_count,
                   defect_quantity,
                   record_ratio,
                   cumulative_ratio
            FROM v_defect_pareto
            """)
    List<DefectParetoRow> defectPareto();

    /** 供应商质量排名：直接读取数据库视图，按召回数/缺陷数/合格率排序。 */
    @Select("""
            SELECT supplier_id,
                   supplier_code,
                   supplier_name,
                   batch_total,
                   iqc_total,
                   iqc_pass,
                   pass_rate,
                   defect_count,
                   recall_count
            FROM v_supplier_quality
            ORDER BY recall_count DESC,
                     defect_count DESC,
                     pass_rate ASC,
                     supplier_id ASC
            """)
    List<SupplierQualityRow> supplierQuality();
}
