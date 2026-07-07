package com.xinghui.qualitytrace.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色权限注解 —— 声明接口所需的角色（RBAC 的接口级实施点）
 *
 * <p>用法：标注在 Controller 方法或类上，{@code @RequireRole({"WAREHOUSE"})}
 * 表示仅仓库人员可调用；数组内任一角色匹配即放行（OR 语义）。</p>
 *
 * <p>特权规则：ADMIN（系统管理员）隐式通过所有 @RequireRole 校验——
 * 与需求文档 §2.1 的角色定义一致（管理员负责全部主数据与用户管理，
 * 演示与排障时也需要全功能视角）。未标注本注解的接口 = 登录即可访问。</p>
 *
 * <p>校验实施于 {@link JwtInterceptor#preHandle}，方法级注解优先于类级。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /** 允许访问的角色编码数组（任一匹配即可，ADMIN 恒通过） */
    String[] value();
}
