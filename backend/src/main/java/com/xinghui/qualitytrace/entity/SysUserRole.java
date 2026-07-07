package com.xinghui.qualitytrace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户-角色关联实体 —— 对应表 sys_user_role（RBAC 的 M:N 关联，
 * 数据库 UNIQUE(user_id, role_id) 防重复授权）
 */
@Data
@TableName("sys_user_role")
public class SysUserRole {

    /** 关联ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 角色ID */
    private Long roleId;

    /** 授权时间 */
    private LocalDateTime createdAt;
}
