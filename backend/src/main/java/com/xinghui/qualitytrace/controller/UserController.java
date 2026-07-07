package com.xinghui.qualitytrace.controller;

import com.xinghui.qualitytrace.common.result.PageResult;
import com.xinghui.qualitytrace.common.result.Result;
import com.xinghui.qualitytrace.dto.common.StatusRequest;
import com.xinghui.qualitytrace.dto.user.ResetPasswordRequest;
import com.xinghui.qualitytrace.dto.user.UserResponse;
import com.xinghui.qualitytrace.dto.user.UserSaveRequest;
import com.xinghui.qualitytrace.entity.SysRole;
import com.xinghui.qualitytrace.security.RequireRole;
import com.xinghui.qualitytrace.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理接口（F1-2 用户管理 / F1-3 角色分配）—— 仅系统管理员可操作
 *
 * <p>类级 @RequireRole("ADMIN")：本控制器全部端点要求管理员角色
 * （ADMIN 在拦截器中恒通过，此处声明表达"其他角色一律拒绝"）。</p>
 */
@Tag(name = "02-用户管理", description = "用户CRUD、启停、重置密码、角色分配（仅管理员）")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@RequireRole("ADMIN")
public class UserController {

    private final UserService userService;

    @Operation(summary = "分页查询用户", description = "keyword 对账号/姓名模糊匹配")
    @GetMapping
    public Result<PageResult<UserResponse>> page(@RequestParam(defaultValue = "1") long current,
                                                 @RequestParam(defaultValue = "10") long size,
                                                 @RequestParam(required = false) String keyword) {
        return Result.ok(userService.page(current, size, keyword));
    }

    @Operation(summary = "角色字典", description = "用户表单角色勾选的数据源（五种预置角色）")
    @GetMapping("/roles")
    public Result<List<SysRole>> roles() {
        return Result.ok(userService.listRoles());
    }

    @Operation(summary = "创建用户", description = "初始密码必填；角色替换式授权")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody UserSaveRequest request) {
        userService.create(request);
        return Result.ok();
    }

    @Operation(summary = "编辑用户", description = "仅改姓名/手机号/角色；不改账号与密码")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UserSaveRequest request) {
        userService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "重置密码", description = "管理员为用户设置新密码（独立通道）")
    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id,
                                      @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request.getNewPassword());
        return Result.ok();
    }

    @Operation(summary = "删除用户", description = "仅允许删除未被业务数据引用的误建账号；历史经办人不可删除")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "启用/停用账号", description = "防呆：不允许停用当前登录账号")
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        userService.changeStatus(id, request.getStatus());
        return Result.ok();
    }
}
