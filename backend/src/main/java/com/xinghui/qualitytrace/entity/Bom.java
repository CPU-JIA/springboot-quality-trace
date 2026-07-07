package com.xinghui.qualitytrace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * BOM 物料清单实体 —— 对应表 bom（物料对自身的 M:N 一元联系"构成"）
 *
 * <p>语义：生产 1 单位父项物料需要 quantity 单位子项物料，支持多级嵌套。
 * 防线分层：直接自引用由数据库 CHECK(parent≠child) 拒绝；跨层成环
 * （A→B→C→A）由 Service 层 DFS 检测（数据库 CHECK 无法表达递归约束）。</p>
 */
@Data
@TableName("bom")
public class Bom {

    /** BOM 行ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父项物料ID（半成品或成品） */
    @NotNull(message = "父项物料不能为空")
    private Long parentMaterialId;

    /** 子项物料ID（原材料或半成品） */
    @NotNull(message = "子项物料不能为空")
    private Long childMaterialId;

    /** 单位用量：生产 1 单位父项所需子项数量 */
    @NotNull(message = "单位用量不能为空")
    @DecimalMin(value = "0.001", message = "单位用量必须为正数")
    private BigDecimal quantity;

    /** 创建时间（数据库维护） */
    private LocalDateTime createdAt;
}
