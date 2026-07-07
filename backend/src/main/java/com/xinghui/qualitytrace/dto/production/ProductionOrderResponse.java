package com.xinghui.qualitytrace.dto.production;

import com.xinghui.qualitytrace.common.enums.MaterialCategory;
import com.xinghui.qualitytrace.common.enums.OrderStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 生产工单列表响应 —— 工单分页行直接带生产物料名称，避免前端为显示列表全量加载物料表。
 */
@Data
public class ProductionOrderResponse {

    /** 工单ID */
    private Long id;

    /** 工单号 */
    private String orderNo;

    /** 生产物料ID */
    private Long materialId;

    /** 生产物料编码 */
    private String materialCode;

    /** 生产物料名称 */
    private String materialName;

    /** 生产物料类别 */
    private MaterialCategory materialCategory;

    /** 计划数量 */
    private BigDecimal planQuantity;

    /** 实际数量 */
    private BigDecimal actualQuantity;

    /** 工单状态 */
    private OrderStatus status;

    /** 计划开始日期 */
    private LocalDate planStartDate;

    /** 计划结束日期 */
    private LocalDate planEndDate;

    /** 实际开工时间 */
    private LocalDateTime actualStartTime;

    /** 实际完工时间 */
    private LocalDateTime actualEndTime;

    /** 负责人ID */
    private Long managerId;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
