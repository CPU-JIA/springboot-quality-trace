package com.xinghui.qualitytrace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xinghui.qualitytrace.common.enums.InspectType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 检验项目实体 —— 对应表 inspection_item（检验标准的最小单元）
 *
 * <p>互斥约束（数据库 CHECK chk_item_ipqc_process 强制，Service 层友好前置校验）：
 * IPQC 项目必须挂靠工序（processDefId 非空）；IQC/FQC 项目不挂工序。</p>
 */
@Data
@TableName("inspection_item")
public class InspectionItem {

    /** 检验项目ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目编码（如 II-001，唯一） */
    @NotBlank(message = "项目编码不能为空")
    private String itemCode;

    /** 项目名称（如 触点接触电阻） */
    @NotBlank(message = "项目名称不能为空")
    private String itemName;

    /** 检验类型：IQC=来料 IPQC=过程 FQC=成品 */
    @NotNull(message = "检验类型不能为空")
    private InspectType inspectType;

    /** 适用物料ID（该物料做此类检验时需检此项） */
    @NotNull(message = "适用物料不能为空")
    private Long materialId;

    /** 挂靠工序ID（仅 IPQC 项目填写：该工序完工时检验） */
    private Long processDefId;

    /** 是否定量：1=录实测值按上下限判定 0=定性人工判定 */
    @NotNull(message = "是否定量不能为空")
    private Integer isQuantitative;

    /** 检验标准描述（判定依据的文字表述） */
    @NotBlank(message = "检验标准描述不能为空")
    private String standardDesc;

    /** 下限（定量项，可单边） */
    private BigDecimal lowerLimit;

    /** 上限（定量项，可单边） */
    private BigDecimal upperLimit;

    /** 计量单位（如 mΩ / V） */
    private String unit;

    /** 创建时间（数据库维护） */
    private LocalDateTime createdAt;
}
