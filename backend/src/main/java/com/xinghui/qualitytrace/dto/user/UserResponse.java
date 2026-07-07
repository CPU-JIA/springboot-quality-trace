package com.xinghui.qualitytrace.dto.user;

import com.xinghui.qualitytrace.entity.SysRole;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户列表/详情响应体 —— 实体脱敏投影（绝不外泄 password 摘要）+ 角色装配
 */
@Getter
@Builder
public class UserResponse {

    /** 用户ID */
    private final Long id;

    /** 登录账号 */
    private final String username;

    /** 真实姓名 */
    private final String realName;

    /** 手机号 */
    private final String phone;

    /** 账号状态：1=启用 0=禁用 */
    private final Integer status;

    /** 持有的角色（含编码与名称，前端表格标签展示） */
    private final List<SysRole> roles;

    /** 创建时间 */
    private final LocalDateTime createdAt;
}
