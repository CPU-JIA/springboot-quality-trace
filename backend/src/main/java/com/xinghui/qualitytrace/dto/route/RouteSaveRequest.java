package com.xinghui.qualitytrace.dto.route;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

/**
 * 工艺路线保存请求体 —— 整条替换语义（PUT）：以 steps 为准整体覆盖该物料的路线
 */
@Data
public class RouteSaveRequest {

    /** 物料ID（被生产的半成品/成品） */
    @NotNull(message = "物料不能为空")
    private Long materialId;

    /** 工序步骤列表（stepNo 必须从 1 连续递增） */
    @NotEmpty(message = "工艺路线至少包含一道工序")
    @Valid
    private List<RouteStep> steps;

    /** 单个步骤 */
    @Data
    public static class RouteStep {

        /** 工序ID */
        @NotNull(message = "工序不能为空")
        private Long processDefId;

        /** 步骤序号 */
        @NotNull(message = "步骤序号不能为空")
        @Min(value = 1, message = "步骤序号必须从1开始")
        private Integer stepNo;
    }
}
