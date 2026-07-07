package com.xinghui.qualitytrace.dto.recall;

import com.xinghui.qualitytrace.common.enums.RecoveryStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 召回明细状态更新请求体 —— 逐条跟踪通知/回收进度（F7-2）
 */
@Data
public class RecallDetailUpdateRequest {

    /** 回收状态 */
    @NotNull(message = "回收状态不能为空")
    private RecoveryStatus recoveryStatus;

    /** 备注 */
    private String remark;
}
