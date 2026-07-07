package com.xinghui.qualitytrace.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xinghui.qualitytrace.common.enums.BatchStatus;
import com.xinghui.qualitytrace.common.enums.MaterialCategory;
import com.xinghui.qualitytrace.common.enums.SourceType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 批次全景视图实体 —— 对应数据库视图 v_batch_overview
 *
 * <p>该视图封装批次台账高频联接：批次 + 物料 + 供应商 + 工单 + 登记人。
 * 它是只读投影，不提供插入/更新接口。</p>
 */
@Data
@TableName("v_batch_overview")
public class BatchOverview {

    /** 批次ID */
    @TableId
    private Long id;

    /** 批次号 */
    private String batchNo;

    /** 物料ID */
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 物料类别 */
    private MaterialCategory materialCategory;

    /** 计量单位 */
    private String unit;

    /** 来源类型 */
    private SourceType sourceType;

    /** 供应商名称（采购批次） */
    private String supplierName;

    /** 工单号（生产批次） */
    private String productionOrderNo;

    /** 初始数量 */
    private BigDecimal quantity;

    /** 剩余数量 */
    private BigDecimal remainingQuantity;

    /** 批次状态 */
    private BatchStatus status;

    /** 生产日期/入库日期 */
    private LocalDate productionDate;

    /** 到期日期 */
    private LocalDate expireDate;

    /** 库位 */
    private String warehouseLocation;

    /** 登记人姓名 */
    private String createdByName;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
