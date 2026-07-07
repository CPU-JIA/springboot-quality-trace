package com.xinghui.qualitytrace.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求体 —— 防御编程：服务端强制非空校验（@Validated 触发），前端校验仅为体验
 */
@Data
public class LoginRequest {

    /** 登录账号 */
    @NotBlank(message = "账号不能为空")
    private String username;

    /** 登录密码（明文仅存在于本次请求内存中，服务端只与 BCrypt 摘要比对） */
    @NotBlank(message = "密码不能为空")
    private String password;
}
