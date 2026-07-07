package com.xinghui.qualitytrace.dto.production;

import lombok.Data;

/**
 * 工序报完工请求体 —— 当前仅收备注，完工时间由服务端统一取当前时间
 */
@Data
public class ProcessCompleteRequest {

    /** 报工备注 */
    private String remark;
}
