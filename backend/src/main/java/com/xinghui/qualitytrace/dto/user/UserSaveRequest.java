package com.xinghui.qualitytrace.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 用户新增/编辑请求体
 *
 * <p>密码字段的双场景语义：创建时必填（Service 校验）；编辑时忽略本字段
 * （密码只能走独立的"重置密码"通道，防止编辑表单误改密码）。</p>
 */
@Data
public class UserSaveRequest {

    /** 登录账号：4~20 位字母数字下划线 */
    @NotBlank(message = "账号不能为空")
    @Pattern(regexp = "^\\w{4,20}$", message = "账号须为4~20位字母、数字或下划线")
    private String username;

    /** 初始密码（仅创建时使用；编辑时传入亦被忽略） */
    @Size(min = 6, max = 32, message = "密码长度须为6~32位")
    private String password;

    /** 真实姓名 */
    @NotBlank(message = "姓名不能为空")
    private String realName;

    /** 手机号 */
    private String phone;

    /** 授予的角色ID列表（替换式：以本列表为准整体覆盖） */
    private List<Long> roleIds;
}
