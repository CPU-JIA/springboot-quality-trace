package com.xinghui.qualitytrace.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinghui.qualitytrace.common.enums.MaterialCategory;
import com.xinghui.qualitytrace.common.exception.BusinessException;
import com.xinghui.qualitytrace.common.result.PageBounds;
import com.xinghui.qualitytrace.common.result.PageResult;
import com.xinghui.qualitytrace.entity.Material;
import com.xinghui.qualitytrace.mapper.MaterialMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 物料服务 —— 原材料/半成品/成品统一管理（F2-1）
 *
 * <p>停用闸门：物料尚有"在库"批次（余量>0 且处于可流转态）时拒绝停用——
 * 否则这些批次的检验/领料/出货流程会悬空（口径见 MaterialMapper.countActiveBatches）。</p>
 */
@Service
@RequiredArgsConstructor
public class MaterialService {

    private final MaterialMapper materialMapper;

    /** 分页查询（keyword 模糊匹配编码/名称；category 精确筛选，可空） */
    public PageResult<Material> page(long current, long size, String keyword, MaterialCategory category, Integer status) {
        Page<Material> page = materialMapper.selectPage(PageBounds.page(current, size),
                new LambdaQueryWrapper<Material>()
                        .and(StrUtil.isNotBlank(keyword), w -> w
                                .like(Material::getMaterialCode, keyword)
                                .or().like(Material::getName, keyword))
                        .eq(category != null, Material::getCategory, category)
                        .eq(status != null, Material::getStatus, status)
                        .orderByAsc(Material::getId));
        return PageResult.of(page);
    }

    /** 新增物料（强制清 id 防客户端伪造为更新；编码重复由 UNIQUE 兜底） */
    public void create(Material material) {
        material.setId(null);
        material.setStatus(1);
        material.setCreatedAt(null);
        material.setUpdatedAt(null);
        materialMapper.insert(material);
    }

    /** 编辑物料（不允许改类别——类别决定批次号前缀与业务规则，改动会破坏既有批次语义） */
    public void update(Long id, Material material) {
        Material existing = requireMaterial(id);
        requireStatus(material.getStatus());
        if (existing.getCategory() != material.getCategory()) {
            throw new BusinessException("物料类别不允许修改（已有批次语义依赖类别）");
        }
        if (material.getStatus() != null && material.getStatus() == 0
                && (existing.getStatus() == null || existing.getStatus() != 0)) {
            ensureNoActiveBatches(id);
        }
        material.setId(id);
        material.setCreatedAt(null);
        material.setUpdatedAt(null);
        materialMapper.updateById(material);
    }

    /** 删除物料（有任何批次/BOM/路线引用时被外键 RESTRICT 拒绝 → 全局友好提示） */
    public void delete(Long id) {
        requireMaterial(id);
        materialMapper.deleteById(id);
    }

    /** 启用/停用（停用前校验无在库批次） */
    public void changeStatus(Long id, Integer status) {
        Material material = requireMaterial(id);
        if (status == 0) {
            ensureNoActiveBatches(id);
        }
        material.setStatus(status);
        materialMapper.updateById(material);
    }

    private void ensureNoActiveBatches(Long id) {
        long active = materialMapper.countActiveBatches(id);
        if (active > 0) {
            throw new BusinessException("该物料尚有 " + active + " 个在库批次，不可停用");
        }
    }

    private void requireStatus(Integer status) {
        if (status == null) {
            throw new BusinessException("状态不能为空");
        }
    }

    private Material requireMaterial(Long id) {
        Material material = materialMapper.selectById(id);
        if (material == null) {
            throw new BusinessException("物料不存在或已被删除");
        }
        return material;
    }
}
