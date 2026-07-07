package com.xinghui.qualitytrace.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 重置密码请求体（管理员操作，独立通道防误改） */
@Data
public class ResetPasswordRequest {

    /** 新密码 */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度须为6~32位")
    private String newPassword;
}
