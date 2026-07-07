package com.xinghui.qualitytrace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinghui.qualitytrace.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;

/** 角色 Mapper —— 纯单表查询（五种角色由演示数据预置，系统不提供角色增删） */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {
}
