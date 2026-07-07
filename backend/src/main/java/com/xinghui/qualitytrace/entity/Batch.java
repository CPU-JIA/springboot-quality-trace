package com.xinghui.qualitytrace.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xinghui.qualitytrace.common.enums.BatchStatus;
import com.xinghui.qualitytrace.common.enums.SourceType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 批次实体 —— 对应表 batch（全系统追溯基本单元，兼任库存）
 *
 * <p>来源互斥规则由数据库 CHECK 兜底、Service 层前置校验：
 * 采购批次（PURCHASE）必须关联供应商且无工单；生产批次（PRODUCTION）必须关联工单且无供应商。</p>
 */
@Data
@TableName("batch")
public class Batch {

    /** 批次ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 批次号（RM/SF/FP-yyyyMMdd-3位序号） */
    private String batchNo;

    /** 所属物料ID */
    private Long materialId;

    /** 来源类型：PURCHASE=采购入库 PRODUCTION=生产完工 */
    private SourceType sourceType;

    /** 供应商ID（仅采购批次） */
    private Long supplierId;

    /** 产出工单ID（仅生产批次） */
    private Long productionOrderId;

    /** 初始数量 */
    private BigDecimal quantity;

    /** 剩余数量（库存语义） */
    private BigDecimal remainingQuantity;

    /** 批次状态 */
    private BatchStatus status;

    /** 生产日期（采购批次为入库日期） */
    private LocalDate productionDate;

    /** 到期日期（物料无保质期则为空） */
    private LocalDate expireDate;

    /** 仓库库位 */
    private String warehouseLocation;

    /** 登记人ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
