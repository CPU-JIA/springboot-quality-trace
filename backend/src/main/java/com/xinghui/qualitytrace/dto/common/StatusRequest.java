package com.xinghui.qualitytrace.dto.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 启用/停用状态变更请求体（用户/主数据通用） */
@Data
public class StatusRequest {

    /** 目标状态：1=启用 0=停用 */
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态取值只能为0或1")
    @Max(value = 1, message = "状态取值只能为0或1")
    private Integer status;
}
