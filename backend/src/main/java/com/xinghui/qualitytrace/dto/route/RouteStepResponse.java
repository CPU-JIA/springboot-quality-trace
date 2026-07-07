package com.xinghui.qualitytrace.dto.route;

import lombok.Builder;
import lombok.Getter;

/**
 * 工艺路线步骤响应体 —— 路线查询的行结构（含工序信息装配，前端免二次查询）
 */
@Getter
@Builder
public class RouteStepResponse {

    /** 步骤序号 */
    private final Integer stepNo;

    /** 工序ID */
    private final Long processDefId;

    /** 工序编码 */
    private final String processCode;

    /** 工序名称 */
    private final String processName;

    /** 是否 IPQC 检验点（前端以徽标提示） */
    private final Integer needIpqc;
}
