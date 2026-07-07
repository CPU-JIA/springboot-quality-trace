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
 * 客户实体 —— 对应表 customer（成品出货的去向，召回通知的对象）
 */
@Data
@TableName("customer")
public class Customer {

    /** 客户ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客户编码（如 CUS-001，唯一） */
    @NotBlank(message = "客户编码不能为空")
    private String customerCode;

    /** 客户名称 */
    @NotBlank(message = "客户名称不能为空")
    private String name;

    /** 联系人 */
    private String contactPerson;

    /** 联系电话 */
    private String phone;

    /** 地址 */
    private String address;

    /** 合作状态：1=合作中 0=停用 */
    @Min(value = 0, message = "状态取值只能为0或1")
    @Max(value = 1, message = "状态取值只能为0或1")
    private Integer status;

    /** 创建时间（数据库维护） */
    private LocalDateTime createdAt;

    /** 更新时间（数据库维护） */
    private LocalDateTime updatedAt;
}
