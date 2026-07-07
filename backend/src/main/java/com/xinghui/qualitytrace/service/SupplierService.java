package com.xinghui.qualitytrace.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinghui.qualitytrace.common.exception.BusinessException;
import com.xinghui.qualitytrace.common.result.PageBounds;
import com.xinghui.qualitytrace.common.result.PageResult;
import com.xinghui.qualitytrace.entity.Supplier;
import com.xinghui.qualitytrace.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 供应商服务 —— 供应商档案维护（F2-5）
 *
 * <p>停用语义：停用后不可再为其登记入库（批次入库时校验），
 * 历史批次的追溯与统计不受影响（外键仍在）。</p>
 */
@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierMapper supplierMapper;

    /** 分页查询（keyword 模糊匹配编码/名称） */
    public PageResult<Supplier> page(long current, long size, String keyword, Integer status) {
        Page<Supplier> page = supplierMapper.selectPage(PageBounds.page(current, size),
                new LambdaQueryWrapper<Supplier>()
                        .and(StrUtil.isNotBlank(keyword), w -> w
                                .like(Supplier::getSupplierCode, keyword)
                                .or().like(Supplier::getName, keyword))
                        .eq(status != null, Supplier::getStatus, status)
                        .orderByAsc(Supplier::getId));
        return PageResult.of(page);
    }

    /** 新增（清 id 防伪造更新） */
    public void create(Supplier supplier) {
        supplier.setId(null);
        supplier.setStatus(1);
        supplier.setCreatedAt(null);
        supplier.setUpdatedAt(null);
        supplierMapper.insert(supplier);
    }

    /** 编辑 */
    public void update(Long id, Supplier supplier) {
        requireSupplier(id);
        requireStatus(supplier.getStatus());
        supplier.setId(id);
        supplier.setCreatedAt(null);
        supplier.setUpdatedAt(null);
        supplierMapper.updateById(supplier);
    }

    /** 删除（有批次引用时被外键 RESTRICT 拒绝 → 全局友好提示改用停用） */
    public void delete(Long id) {
        requireSupplier(id);
        supplierMapper.deleteById(id);
    }

    private Supplier requireSupplier(Long id) {
        Supplier supplier = supplierMapper.selectById(id);
        if (supplier == null) {
            throw new BusinessException("供应商不存在或已被删除");
        }
        return supplier;
    }

    private void requireStatus(Integer status) {
        if (status == null) {
            throw new BusinessException("状态不能为空");
        }
    }
}
