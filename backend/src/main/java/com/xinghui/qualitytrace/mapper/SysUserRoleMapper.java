package com.xinghui.qualitytrace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinghui.qualitytrace.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;

/** 用户-角色关联 Mapper —— 角色分配在 Service 事务内以 delete+insert 整体替换 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {
}
