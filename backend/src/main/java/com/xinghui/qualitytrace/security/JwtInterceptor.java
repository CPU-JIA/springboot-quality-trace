package com.xinghui.qualitytrace.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xinghui.qualitytrace.common.result.Result;
import com.xinghui.qualitytrace.entity.SysUser;
import com.xinghui.qualitytrace.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * JWT 认证 + 角色鉴权拦截器 —— 自实现认证链路的核心（不引 Spring Security 过滤器链）
 *
 * <p>处理流程（preHandle）：
 * <ol>
 *   <li>非业务处理器（静态资源等）直接放行；</li>
 *   <li>从 Authorization 头提取 Bearer 令牌，解析失败/缺失 → 401；</li>
 *   <li>解析成功则绑定 {@link UserContext}（供审计填充与业务取用）；</li>
 *   <li>读取处理方法/类上的 {@link RequireRole} 注解做角色校验（方法级优先），
 *       ADMIN 隐式通过；不满足 → 403。</li>
 * </ol>
 * afterCompletion 无条件清理 ThreadLocal，防线程池身份串号。</p>
 *
 * <p>挂载范围由 WebConfig 决定：仅拦 /api/**，白名单 /api/auth/login；
 * swagger 与 druid 不在 /api 下天然不受影响。</p>
 */
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    /** ADMIN 为超级角色：隐式通过一切 @RequireRole 校验 */
    private static final String SUPER_ROLE = "ADMIN";

    private final JwtUtil jwtUtil;
    private final SysUserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {
        // 只对 Controller 方法做认证（静态资源/错误页处理器直接放行）
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // ---- 第一步：认证（有没有合法令牌） ----
        String header = request.getHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return reject(response, 401, "未登录或令牌缺失");
        }
        UserContext.LoginUser user = jwtUtil.parse(header.substring(BEARER_PREFIX.length()));
        if (user == null) {
            return reject(response, 401, "令牌无效或已过期，请重新登录");
        }
        SysUser currentUser = userMapper.selectById(user.getUserId());
        if (currentUser == null) {
            return reject(response, 401, "用户不存在，请重新登录");
        }
        if (currentUser.getStatus() == null || currentUser.getStatus() != 1) {
            return reject(response, 401, "账号已被禁用，请重新登录");
        }
        user = new UserContext.LoginUser(
                currentUser.getId(),
                currentUser.getUsername(),
                userMapper.selectRoleCodesByUserId(currentUser.getId()));
        // 认证通过：绑定请求级用户上下文
        UserContext.set(user);

        // ---- 第二步：鉴权（角色是否满足 @RequireRole，方法级注解优先于类级） ----
        RequireRole required = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (required == null) {
            required = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (required != null && !hasRole(user, required.value())) {
            return reject(response, 403, "当前角色无权执行该操作");
        }
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler, Exception ex) {
        // 无条件清理：Tomcat 线程池复用线程，不清理会造成用户身份串号
        UserContext.clear();
    }

    /** 角色判定：ADMIN 恒通过；否则要求持有注解声明的任一角色 */
    private boolean hasRole(UserContext.LoginUser user, String[] requiredRoles) {
        if (user.getRoles() == null) {
            return false;
        }
        if (user.getRoles().contains(SUPER_ROLE)) {
            return true;
        }
        return Arrays.stream(requiredRoles).anyMatch(user.getRoles()::contains);
    }

    /** 以统一 Result 结构写出拒绝响应（拦截器不经过 @RestControllerAdvice，需自行序列化） */
    private boolean reject(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(200);   // 业务码承载语义，HTTP 层统一 200（前后端契约）
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(code, message)));
        return false;
    }
}
