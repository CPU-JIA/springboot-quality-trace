package com.xinghui.qualitytrace.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinghui.qualitytrace.common.exception.BusinessException;
import com.xinghui.qualitytrace.common.result.PageBounds;
import com.xinghui.qualitytrace.common.result.PageResult;
import com.xinghui.qualitytrace.entity.Customer;
import com.xinghui.qualitytrace.mapper.CustomerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 客户服务 —— 客户档案维护（F2-6），结构与供应商对称
 */
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerMapper customerMapper;

    /** 分页查询（keyword 模糊匹配编码/名称） */
    public PageResult<Customer> page(long current, long size, String keyword, Integer status) {
        Page<Customer> page = customerMapper.selectPage(PageBounds.page(current, size),
                new LambdaQueryWrapper<Customer>()
                        .and(StrUtil.isNotBlank(keyword), w -> w
                                .like(Customer::getCustomerCode, keyword)
                                .or().like(Customer::getName, keyword))
                        .eq(status != null, Customer::getStatus, status)
                        .orderByAsc(Customer::getId));
        return PageResult.of(page);
    }

    /** 新增（清 id 防伪造更新） */
    public void create(Customer customer) {
        customer.setId(null);
        customer.setStatus(1);
        customer.setCreatedAt(null);
        customer.setUpdatedAt(null);
        customerMapper.insert(customer);
    }

    /** 编辑 */
    public void update(Long id, Customer customer) {
        requireCustomer(id);
        requireStatus(customer.getStatus());
        customer.setId(id);
        customer.setCreatedAt(null);
        customer.setUpdatedAt(null);
        customerMapper.updateById(customer);
    }

    /** 删除（有出货/召回明细引用时被外键 RESTRICT 拒绝） */
    public void delete(Long id) {
        requireCustomer(id);
        customerMapper.deleteById(id);
    }

    private Customer requireCustomer(Long id) {
        Customer customer = customerMapper.selectById(id);
        if (customer == null) {
            throw new BusinessException("客户不存在或已被删除");
        }
        return customer;
    }

    private void requireStatus(Integer status) {
        if (status == null) {
            throw new BusinessException("状态不能为空");
        }
    }
}
