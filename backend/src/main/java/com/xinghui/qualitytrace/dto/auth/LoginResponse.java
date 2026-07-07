package com.xinghui.qualitytrace.dto.auth;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 登录响应体 —— 前端据此建立会话：token 存 localStorage 并挂 axios 请求头；
 * roles 驱动侧边菜单渲染与按钮可见性
 */
@Getter
@Builder
public class LoginResponse {

    /** JWT 令牌（后续请求以 Authorization: Bearer <token> 携带） */
    private final String token;

    /** 用户ID */
    private final Long userId;

    /** 登录账号 */
    private final String username;

    /** 真实姓名（页面右上角展示） */
    private final String realName;

    /** 角色编码列表（前端菜单/路由守卫依据） */
    private final List<String> roles;
}
