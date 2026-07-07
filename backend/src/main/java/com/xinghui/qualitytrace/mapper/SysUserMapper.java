package com.xinghui.qualitytrace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinghui.qualitytrace.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户 Mapper —— 继承 BaseMapper 获得单表 CRUD；角色编码查询为跨表联接，注解 SQL 实现
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 查询用户持有的全部角色编码（登录签发 JWT 时装入 roles claim）。
     * 联接路径：sys_user_role（M:N 关联）→ sys_role 取编码。
     *
     * @param userId 用户ID
     * @return 角色编码列表（如 ["INSPECTOR"]），无角色时为空列表
     */
    @Select("""
            SELECT r.role_code
            FROM sys_user_role ur
                     JOIN sys_role r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
            """)
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);
}
