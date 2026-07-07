package com.xinghui.qualitytrace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinghui.qualitytrace.common.exception.BusinessException;
import com.xinghui.qualitytrace.dto.auth.LoginRequest;
import com.xinghui.qualitytrace.dto.auth.LoginResponse;
import com.xinghui.qualitytrace.entity.SysUser;
import com.xinghui.qualitytrace.mapper.SysUserMapper;
import com.xinghui.qualitytrace.security.JwtUtil;
import com.xinghui.qualitytrace.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 认证服务 —— 登录校验与用户信息查询
 *
 * <p>登录安全设计（答辩要点）：
 * <ul>
 *   <li>账号不存在与密码错误返回【同一提示】——防账号枚举探测；</li>
 *   <li>密码比对用 BCrypt matches（摘要含盐，服务端全程不接触存量明文）；</li>
 *   <li>禁用账号（status=0）明确拒绝——离职/违规人员的访问闸门。</li>
 * </ul></p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 账号或密码错误的统一话术：不区分"账号不存在/密码不对"，防枚举 */
    private static final String BAD_CREDENTIALS = "账号或密码错误";

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * 登录：账号密码校验通过后签发 JWT
     *
     * @param request 登录请求（账号/密码已经过非空校验）
     * @return 令牌与用户概要
     * @throws BusinessException 账号密码错误 / 账号被禁用
     */
    public LoginResponse login(LoginRequest request) {
        // 1. 按账号查用户（username 有唯一索引，至多一条）
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername()));
        if (user == null) {
            throw new BusinessException(BAD_CREDENTIALS);
        }

        // 2. BCrypt 摘要比对（matches 内部提取盐重算，恒定与否与明文长度无关）
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(BAD_CREDENTIALS);
        }

        // 3. 账号状态闸门：禁用账号即使密码正确也不放行
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }

        // 4. 装载角色并签发令牌（角色写入 JWT claims，后续请求免查库鉴权）
        List<String> roles = userMapper.selectRoleCodesByUserId(user.getId());
        String token = jwtUtil.generate(user.getId(), user.getUsername(), roles);
        log.info("用户登录成功: {}({}), 角色{}", user.getUsername(), user.getRealName(), roles);

        return LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .roles(roles)
                .build();
    }

    /**
     * 查询当前登录用户概要（前端刷新页面后恢复会话信息用）
     *
     * @return 不含令牌的用户概要（复用 LoginResponse 结构，token 为 null）
     */
    public LoginResponse profile() {
        Long userId = UserContext.currentUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            // 令牌合法但用户已被物理删除的极端场景（本系统用户不物理删，防御性兜底）
            throw new BusinessException(401, "用户不存在，请重新登录");
        }
        return LoginResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .roles(userMapper.selectRoleCodesByUserId(userId))
                .build();
    }
}
