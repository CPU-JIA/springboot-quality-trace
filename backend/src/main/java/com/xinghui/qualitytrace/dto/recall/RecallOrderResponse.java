package com.xinghui.qualitytrace.dto.recall;

import com.xinghui.qualitytrace.common.enums.RecallLevel;
import com.xinghui.qualitytrace.common.enums.RecallStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 召回单响应体 —— 召回主信息 + 源头批次号 + 明细列表
 */
@Getter
@Builder
public class RecallOrderResponse {

    private final Long id;
    private final String recallNo;
    private final Long sourceBatchId;
    private final String sourceBatchNo;
    private final RecallLevel recallLevel;
    private final String reason;
    private final RecallStatus status;
    private final Long initiatorId;
    private final LocalDateTime completedAt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final List<RecallDetailResponse> details;
}
