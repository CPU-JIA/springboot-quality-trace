package com.xinghui.qualitytrace.service;

import com.xinghui.qualitytrace.common.exception.BusinessException;
import com.xinghui.qualitytrace.mapper.SerialNumberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 业务单号服务 —— 所有按日期前缀递增的单号统一从 serial_number 原子分配。 */
@Service
@RequiredArgsConstructor
public class SerialNumberService {

    private final SerialNumberMapper serialNumberMapper;

    @Transactional(rollbackFor = Exception.class)
    public String nextNo(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new BusinessException("单号前缀不能为空");
        }
        serialNumberMapper.allocate(prefix);
        Integer seq = serialNumberMapper.lastAllocatedValue();
        if (seq == null || seq <= 0) {
            throw new BusinessException("单号分配失败，请重试");
        }
        return prefix + "-" + String.format("%03d", seq);
    }
}
