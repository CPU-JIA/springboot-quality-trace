package com.xinghui.qualitytrace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinghui.qualitytrace.common.exception.BusinessException;
import com.xinghui.qualitytrace.common.result.PageBounds;
import com.xinghui.qualitytrace.common.result.PageResult;
import com.xinghui.qualitytrace.dto.user.UserResponse;
import com.xinghui.qualitytrace.dto.user.UserSaveRequest;
import com.xinghui.qualitytrace.entity.SysRole;
import com.xinghui.qualitytrace.entity.SysUser;
import com.xinghui.qualitytrace.entity.SysUserRole;
import com.xinghui.qualitytrace.mapper.SysRoleMapper;
import com.xinghui.qualitytrace.mapper.SysUserMapper;
import com.xinghui.qualitytrace.mapper.SysUserRoleMapper;
import com.xinghui.qualitytrace.security.UserContext;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户管理服务 —— 用户 CRUD / 启停 / 重置密码 / 角色分配（F1-2、F1-3）
 *
 * <p>安全设计要点：
 * <ul>
 *   <li>密码只进不出：创建/重置时 BCrypt 加密落库，任何查询接口不返回摘要；</li>
 *   <li>编辑与改密分离：编辑接口忽略密码字段，防表单误改；</li>
 *   <li>自锁防呆：禁止停用当前登录账号（管理员把自己停了会锁死系统）。</li>
 * </ul></p>
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 分页查询用户（含角色装配）
     *
     * @param current 页码（从 1 起）
     * @param size    页大小
     * @param keyword 关键字（账号/姓名模糊匹配，可空）
     */
    public PageResult<UserResponse> page(long current, long size, String keyword) {
        Page<SysUser> page = userMapper.selectPage(PageBounds.page(current, size),
                new LambdaQueryWrapper<SysUser>()
                        .and(StrUtil.isNotBlank(keyword), w -> w
                                .like(SysUser::getUsername, keyword)
                                .or().like(SysUser::getRealName, keyword))
                        .orderByAsc(SysUser::getId));

        // 批量装配角色：一次查出本页用户的全部关联，内存分组——避免每行一次查询的 N+1 问题
        List<Long> userIds = page.getRecords().stream().map(SysUser::getId).toList();
        Map<Long, List<SysRole>> rolesByUser = loadRolesGrouped(userIds);

        List<UserResponse> rows = page.getRecords().stream()
                .map(u -> toResponse(u, rolesByUser.getOrDefault(u.getId(), List.of())))
                .toList();
        return PageResult.of(rows, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 角色字典（用户表单的角色勾选数据源；五种角色预置，不提供增删） */
    public List<SysRole> listRoles() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getId));
    }

    /**
     * 创建用户（事务：用户 + 角色授权原子提交）
     */
    @Transactional(rollbackFor = Exception.class)
    public void create(UserSaveRequest request) {
        // 创建场景密码必填（编辑场景可空，故不能放注解校验，须在此分场景判断）
        if (StrUtil.isBlank(request.getPassword())) {
            throw new BusinessException("初始密码不能为空");
        }
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        // BCrypt 单向加密：即使数据库泄露也无法还原明文
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setPhone(request.getPhone());
        user.setStatus(1);
        userMapper.insert(user);   // 账号重复由 UNIQUE 兜底 → 全局 DuplicateKey 提示

        replaceRoles(user.getId(), request.getRoleIds());
    }

    /**
     * 编辑用户基本信息与角色（事务）。注意：本接口不改密码、不改账号
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UserSaveRequest request) {
        SysUser user = requireUser(id);
        user.setRealName(request.getRealName());
        user.setPhone(request.getPhone());
        userMapper.updateById(user);

        ensureSelfAdminRoleRetained(id, request.getRoleIds());
        replaceRoles(id, request.getRoleIds());
    }

    /** 重置密码（管理员操作，独立通道） */
    public void resetPassword(Long id, String newPassword) {
        SysUser user = requireUser(id);
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
    }

    /**
     * 删除用户。
     *
     * <p>历史业务数据中已经被工单、批次、检验、出货等引用的用户，会被数据库外键
     * ON DELETE RESTRICT 拒绝，并由全局异常处理器转译为"已被引用不可删除"；
     * 这样既支持误建账号清理，又不破坏质量追溯审计链。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (Objects.equals(id, UserContext.currentUserId())) {
            throw new BusinessException("不能删除当前登录账号");
        }
        requireUser(id);
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, id));
        userMapper.deleteById(id);
    }

    /** 启用/停用账号（防呆：不允许停用当前登录账号） */
    public void changeStatus(Long id, Integer status) {
        if (status == 0 && Objects.equals(id, UserContext.currentUserId())) {
            throw new BusinessException("不能停用当前登录账号");
        }
        SysUser user = requireUser(id);
        user.setStatus(status);
        userMapper.updateById(user);
    }

    // ---------------- 私有辅助 ----------------

    /** 按ID取用户，不存在即抛业务异常（统一的存在性防线） */
    private SysUser requireUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在或已被删除");
        }
        return user;
    }

    /** 替换式角色授权：删旧插新（在调用方事务内执行，保证原子性） */
    private void replaceRoles(Long userId, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            throw new BusinessException("请至少选择一个角色");
        }
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
        // 校验角色ID合法性（防提交不存在的角色）
        List<SysRole> roles = roleMapper.selectByIds(roleIds);
        if (roles.size() != roleIds.stream().distinct().count()) {
            throw new BusinessException("包含不存在的角色");
        }
        for (Long roleId : roleIds.stream().distinct().toList()) {
            SysUserRole relation = new SysUserRole();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            userRoleMapper.insert(relation);
        }
    }

    /**
     * 当前登录管理员不能通过编辑自己移除 ADMIN 角色。
     *
     * <p>系统已经禁止删除/停用当前账号；这里补齐角色授权的同类防呆。由于 JWT 拦截器
     * 每次请求都会重新加载数据库最新角色，一旦允许自降权，保存成功后的下一次请求就会
     * 立即失去管理能力，单管理员演示库会进入无人可维护状态。</p>
     */
    private void ensureSelfAdminRoleRetained(Long userId, List<Long> roleIds) {
        if (!Objects.equals(userId, UserContext.currentUserId())) {
            return;
        }
        List<String> currentRoles = userMapper.selectRoleCodesByUserId(userId);
        if (!currentRoles.contains("ADMIN")) {
            return;
        }
        if (roleIds == null || roleIds.isEmpty()) {
            throw new BusinessException("不能移除自己的管理员角色");
        }
        List<String> requestedRoles = roleMapper.selectByIds(roleIds.stream().distinct().toList()).stream()
                .map(SysRole::getRoleCode)
                .toList();
        if (!requestedRoles.contains("ADMIN")) {
            throw new BusinessException("不能移除自己的管理员角色");
        }
    }

    /** 批量装配"用户ID → 角色列表"映射（一次关联查询 + 内存分组，避免 N+1） */
    private Map<Long, List<SysRole>> loadRolesGrouped(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<SysUserRole> relations = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getUserId, userIds));
        if (relations.isEmpty()) {
            return Map.of();
        }
        Map<Long, SysRole> roleById = roleMapper.selectByIds(
                        relations.stream().map(SysUserRole::getRoleId).distinct().toList())
                .stream().collect(Collectors.toMap(SysRole::getId, Function.identity()));
        return relations.stream().collect(Collectors.groupingBy(
                SysUserRole::getUserId,
                Collectors.mapping(r -> roleById.get(r.getRoleId()), Collectors.toList())));
    }

    /** 实体 → 脱敏响应投影 */
    private UserResponse toResponse(SysUser user, List<SysRole> roles) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .phone(user.getPhone())
                .status(user.getStatus())
                .roles(roles)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
