package com.xinghui.qualitytrace.dto.inspection;

import com.xinghui.qualitytrace.common.enums.Conclusion;
import com.xinghui.qualitytrace.common.enums.DefectType;
import com.xinghui.qualitytrace.common.enums.Severity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 检验提交请求体 —— 提交逐项明细与整单结论（F5-2/F5-3）
 */
@Data
public class InspectionSubmitRequest {

    /** 整单结论 */
    @NotNull(message = "检验结论不能为空")
    private Conclusion conclusion;

    /** 检验任务备注 */
    private String remark;

    /** 逐项检验结果，必须覆盖该对象适用的全部检验标准项 */
    @NotEmpty(message = "检验明细不能为空")
    @Valid
    private List<RecordItem> records;

    /** 让步接收时必须同步登记轻微缺陷 */
    @Valid
    private ConcessionDefect concessionDefect;

    /** 不合格时必须同步登记待处置缺陷 */
    @Valid
    private UnqualifiedDefect unqualifiedDefect;

    @Data
    public static class RecordItem {

        /** 检验项目ID */
        @NotNull(message = "检验项目不能为空")
        private Long inspectionItemId;

        /** 实测值（定量项必填） */
        private BigDecimal measuredValue;

        /** 结果描述（定性项建议填写） */
        private String resultDesc;

        /** 单项判定（定性项必填；定量项由系统按上下限计算，忽略前端传值） */
        private Integer isPass;
    }

    @Data
    public static class ConcessionDefect {

        /** 缺陷类型 */
        @NotNull(message = "让步缺陷类型不能为空")
        private DefectType defectType;

        /** 缺陷数量 */
        @NotNull(message = "让步缺陷数量不能为空")
        @DecimalMin(value = "0.001", message = "让步缺陷数量必须为正数")
        private BigDecimal quantity;

        /** 缺陷描述 */
        @NotBlank(message = "让步缺陷描述不能为空")
        private String description;
    }

    @Data
    public static class UnqualifiedDefect {

        /** 缺陷类型 */
        @NotNull(message = "不合格缺陷类型不能为空")
        private DefectType defectType;

        /** 严重度 */
        @NotNull(message = "不合格缺陷严重度不能为空")
        private Severity severity;

        /** 缺陷数量 */
        @NotNull(message = "不合格缺陷数量不能为空")
        @DecimalMin(value = "0.001", message = "不合格缺陷数量必须为正数")
        private BigDecimal quantity;

        /** 缺陷描述 */
        @NotBlank(message = "不合格缺陷描述不能为空")
        private String description;
    }
}
