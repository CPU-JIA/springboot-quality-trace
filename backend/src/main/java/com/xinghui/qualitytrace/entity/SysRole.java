package com.xinghui.qualitytrace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统角色实体 —— 对应表 sys_role（五种预置角色，权限粒度到菜单级）
 */
@Data
@TableName("sys_role")
public class SysRole {

    /** 角色ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色编码（如 ADMIN / INSPECTOR，程序中据此判断权限） */
    private String roleCode;

    /** 角色显示名称 */
    private String roleName;

    /** 角色职责描述 */
    private String description;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
