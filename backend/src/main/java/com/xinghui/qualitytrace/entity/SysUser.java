package com.xinghui.qualitytrace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户实体 —— 对应表 sys_user（字段注释与 DDL COMMENT 一致）
 */
@Data
@TableName("sys_user")
public class SysUser {

    /** 用户ID（代理主键，数据库自增） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号（唯一） */
    private String username;

    /**
     * 密码 BCrypt 摘要（60 字符，永不存明文）。
     * ★@JsonIgnore：本字段绝不允许出现在任何接口响应中（防摘要外泄被离线爆破）
     */
    @JsonIgnore
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 手机号 */
    private String phone;

    /** 账号状态：1=启用 0=禁用（禁用即无法登录，不做物理删除） */
    private Integer status;

    /** 创建时间（数据库 DEFAULT CURRENT_TIMESTAMP 维护） */
    private LocalDateTime createdAt;

    /** 更新时间（数据库 ON UPDATE CURRENT_TIMESTAMP 维护） */
    private LocalDateTime updatedAt;
}
