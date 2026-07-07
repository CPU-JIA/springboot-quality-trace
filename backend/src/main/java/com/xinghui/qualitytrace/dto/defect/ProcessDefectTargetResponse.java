package com.xinghui.qualitytrace.dto.defect;

import com.xinghui.qualitytrace.common.enums.OrderStatus;
import com.xinghui.qualitytrace.common.enums.ProcessStatus;
import lombok.Data;

/**
 * 过程缺陷可选工序 —— 缺陷登记弹窗的远程选项。
 *
 * <p>该响应只返回当前仍允许人工登记过程缺陷的工序记录：工单生产中、工序进行中、
 * 后续工序尚未开始且工单尚未产出批次。把这个过滤放在后端，避免前端为下拉框全量拉取
 * 工单详情，也避免展示最终会被登记校验拒绝的工序。</p>
 */
@Data
public class ProcessDefectTargetResponse {

    /** 工序执行记录ID */
    private Long id;

    /** 所属工单ID */
    private Long productionOrderId;

    /** 工单号 */
    private String orderNo;

    /** 工单状态 */
    private OrderStatus orderStatus;

    /** 工序定义ID */
    private Long processDefId;

    /** 工序编码 */
    private String processCode;

    /** 工序名称 */
    private String processName;

    /** 工序步骤号 */
    private Integer stepNo;

    /** 工序状态 */
    private ProcessStatus status;

    /** 生产物料编码 */
    private String materialCode;

    /** 生产物料名称 */
    private String materialName;
}
