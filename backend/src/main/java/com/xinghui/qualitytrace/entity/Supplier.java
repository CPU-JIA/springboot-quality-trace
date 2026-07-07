package com.xinghui.qualitytrace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 供应商实体 —— 对应表 supplier（原材料批次的来源，供应商质量排名的统计主体）
 */
@Data
@TableName("supplier")
public class Supplier {

    /** 供应商ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 供应商编码（如 SUP-001，唯一） */
    @NotBlank(message = "供应商编码不能为空")
    private String supplierCode;

    /** 供应商名称 */
    @NotBlank(message = "供应商名称不能为空")
    private String name;

    /** 联系人 */
    private String contactPerson;

    /** 联系电话 */
    private String phone;

    /** 地址 */
    private String address;

    /** 合作状态：1=合作中 0=停用（停用后不可新建入库，历史追溯不受影响） */
    @Min(value = 0, message = "状态取值只能为0或1")
    @Max(value = 1, message = "状态取值只能为0或1")
    private Integer status;

    /** 创建时间（数据库维护） */
    private LocalDateTime createdAt;

    /** 更新时间（数据库维护） */
    private LocalDateTime updatedAt;
}
