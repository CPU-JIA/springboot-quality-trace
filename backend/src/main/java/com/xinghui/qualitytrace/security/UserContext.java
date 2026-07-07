package com.xinghui.qualitytrace.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 登录用户上下文 —— 基于 ThreadLocal 的请求级用户信息载体
 *
 * <p>工作机制：{@link JwtInterceptor} 在请求进入时解析 JWT 并调用 {@link #set}，
 * 业务代码随处通过 {@link #get()} 取当前用户（如审计字段填充、经办人落库）；
 * 拦截器在 afterCompletion 中调用 {@link #clear()} 释放。</p>
 *
 * <p>Why 必须 clear：Tomcat 以线程池复用工作线程，不清理会导致下一个请求
 * 读到上一个用户的身份——典型的越权事故源头，防御编程强制要求。</p>
 */
public final class UserContext {

    private UserContext() {
    }

    /** 每个工作线程独立的登录用户槽位 */
    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    /** 登录用户快照：从 JWT claims 还原，不含敏感信息（无密码） */
    @Getter
    @RequiredArgsConstructor
    public static class LoginUser {
        /** 用户ID（sys_user.id） */
        private final Long userId;
        /** 登录账号 */
        private final String username;
        /** 角色编码集合（如 ADMIN / INSPECTOR），供 @RequireRole 校验 */
        private final List<String> roles;
    }

    /** 绑定当前请求的登录用户（仅由 JwtInterceptor 调用） */
    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    /** 获取当前登录用户；未认证上下文中返回 null（白名单接口场景） */
    public static LoginUser get() {
        return HOLDER.get();
    }

    /** 获取当前用户ID的便捷方法（审计字段/经办人落库高频使用） */
    public static Long currentUserId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getUserId();
    }

    /** 清理线程槽位（防线程池身份串号，请求结束必须调用） */
    public static void clear() {
        HOLDER.remove();
    }
}
