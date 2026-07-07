package com.xinghui.qualitytrace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 出货记录实体 —— 对应表 shipment（批次 × 客户的出货事件）
 *
 * <p>出货是追溯链的末端：正向追溯定位到成品批次后，可继续通过本表定位客户流向。</p>
 */
@Data
@TableName("shipment")
public class Shipment {

    /** 出货记录ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 出货单号（SH-yyyyMMdd-3位序号） */
    private String shipmentNo;

    /** 出货批次ID（必须为合格成品批次） */
    private Long batchId;

    /** 客户ID */
    private Long customerId;

    /** 出货数量 */
    private BigDecimal quantity;

    /** 出货日期 */
    private LocalDate shipDate;

    /** 经办人ID */
    private Long operatorId;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
