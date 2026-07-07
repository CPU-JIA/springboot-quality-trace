package com.xinghui.qualitytrace.dto.recall;

import com.xinghui.qualitytrace.common.enums.RecallLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 发起召回请求体 —— 指定源头批次与召回级别，由系统正向追溯生成影响明细（F7-1）
 */
@Data
public class RecallCreateRequest {

    /** 问题源头批次ID */
    @NotNull(message = "源头批次不能为空")
    private Long sourceBatchId;

    /** 召回级别 */
    @NotNull(message = "召回级别不能为空")
    private RecallLevel recallLevel;

    /** 召回原因 */
    @NotBlank(message = "召回原因不能为空")
    private String reason;
}
