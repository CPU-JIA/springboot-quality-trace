package com.xinghui.qualitytrace.controller;

import com.xinghui.qualitytrace.common.result.Result;
import com.xinghui.qualitytrace.dto.auth.LoginRequest;
import com.xinghui.qualitytrace.dto.auth.LoginResponse;
import com.xinghui.qualitytrace.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口 —— 登录 / 当前用户
 *
 * <p>登出口径（docs 计划申报）：无状态 JWT 采用"客户端令牌废弃"——前端清除
 * 本地令牌即完成登出，服务端不设黑名单（YAGNI，课设场景令牌 24h 自然过期）。</p>
 */
@Tag(name = "01-认证", description = "登录与当前用户信息")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 登录（拦截器白名单，匿名可访问） */
    @Operation(summary = "登录", description = "账号密码校验通过后签发 JWT；演示账号见文档首页描述")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    /** 当前登录用户概要（需携带令牌） */
    @Operation(summary = "当前用户", description = "前端刷新页面后恢复会话信息")
    @GetMapping("/profile")
    public Result<LoginResponse> profile() {
        return Result.ok(authService.profile());
    }
}
