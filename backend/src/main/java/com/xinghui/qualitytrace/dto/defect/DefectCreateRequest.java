package com.xinghui.qualitytrace.dto.defect;

import com.xinghui.qualitytrace.common.enums.DefectType;
import com.xinghui.qualitytrace.common.enums.Severity;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 缺陷登记请求体 —— 支持批次缺陷与 IPQC 过程缺陷双载体（F5-4）
 */
@Data
public class DefectCreateRequest {

    /** 所属批次ID（IQC/FQC/售后缺陷填写） */
    private Long batchId;

    /** 所属工序执行记录ID（IPQC 过程缺陷填写） */
    private Long processRecordId;

    /** 发现于检验单ID（人工/售后登记可为空） */
    private Long inspectionTaskId;

    /** 缺陷类型 */
    @NotNull(message = "缺陷类型不能为空")
    private DefectType defectType;

    /** 严重度 */
    @NotNull(message = "严重度不能为空")
    private Severity severity;

    /** 缺陷数量 */
    @NotNull(message = "缺陷数量不能为空")
    @DecimalMin(value = "0.001", message = "缺陷数量必须为正数")
    private BigDecimal quantity;

    /** 缺陷描述 */
    @NotBlank(message = "缺陷描述不能为空")
    private String description;
}
