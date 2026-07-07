package com.xinghui.qualitytrace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xinghui.qualitytrace.common.enums.MaterialCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 物料实体 —— 对应表 material（原材料/半成品/成品统一建模，docs/02 §4.6）
 *
 * <p>校验注解说明：主数据表单由实体直接接收（POST 时服务端强制清空 id 防伪造），
 * jakarta 校验注解即服务端防御的第一道闸（@Validated 触发）。</p>
 */
@Data
@TableName("material")
public class Material {

    /** 物料ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 物料编码（如 RM-001，唯一，数据库 UNIQUE 兜底） */
    @NotBlank(message = "物料编码不能为空")
    private String materialCode;

    /** 物料名称 */
    @NotBlank(message = "物料名称不能为空")
    private String name;

    /**
     * 物料类别：RAW=原材料 SEMI=半成品 PRODUCT=成品。
     * MyBatis 默认 EnumTypeHandler 按枚举名存取，与 DDL 的 CHECK 值严格一致
     */
    @NotNull(message = "物料类别不能为空")
    private MaterialCategory category;

    /** 规格型号 */
    private String spec;

    /** 计量单位（个/kg/m 等） */
    @NotBlank(message = "计量单位不能为空")
    private String unit;

    /** 保质期天数（null=不限；入库时据此推算批次到期日） */
    @Min(value = 0, message = "保质期天数不能为负数")
    private Integer shelfLifeDays;

    /** 状态：1=启用 0=停用（停用前校验无在库批次） */
    @Min(value = 0, message = "状态取值只能为0或1")
    @Max(value = 1, message = "状态取值只能为0或1")
    private Integer status;

    /** 创建时间（数据库维护） */
    private LocalDateTime createdAt;

    /** 更新时间（数据库维护） */
    private LocalDateTime updatedAt;
}
