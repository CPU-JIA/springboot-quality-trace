package com.xinghui.qualitytrace.dto.defect;

import com.xinghui.qualitytrace.common.enums.HandleMethod;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 缺陷处置请求体 —— 质量主管评审缺陷后的处置结论（F5-5）
 */
@Data
public class DefectHandleRequest {

    /** 处置方式 */
    @NotNull(message = "处置方式不能为空")
    private HandleMethod handleMethod;
}
