package com.xinghui.qualitytrace.dto.production;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 生产领料请求体 —— 一次为工单登记多条批次消耗边（F4-2）
 */
@Data
public class MaterialIssueRequest {

    /** 领料明细 */
    @NotEmpty(message = "领料明细不能为空")
    @Valid
    private List<Item> items;

    /** 单条领料明细 */
    @Data
    public static class Item {

        /** 被消耗批次ID */
        @NotNull(message = "领料批次不能为空")
        private Long batchId;

        /** 消耗数量 */
        @NotNull(message = "领料数量不能为空")
        @DecimalMin(value = "0.001", message = "领料数量必须为正数")
        private BigDecimal quantity;
    }
}
