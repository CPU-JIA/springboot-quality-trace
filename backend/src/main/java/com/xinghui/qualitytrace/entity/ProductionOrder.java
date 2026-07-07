package com.xinghui.qualitytrace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xinghui.qualitytrace.common.enums.OrderStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 生产工单实体 —— 对应表 production_order（生产活动的组织单元）
 *
 * <p>一张工单在本系统中对应一个生产批次：完工入库时由工单生成唯一产出批次，
 * 数据库以 batch.production_order_id UNIQUE 强制"一工单一批次"。</p>
 */
@Data
@TableName("production_order")
public class ProductionOrder {

    /** 工单ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工单号（MO-yyyyMMdd-3位序号） */
    private String orderNo;

    /** 生产物料ID（仅 SEMI/PRODUCT） */
    private Long materialId;

    /** 计划生产数量 */
    private BigDecimal planQuantity;

    /** 实际完工数量 */
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
