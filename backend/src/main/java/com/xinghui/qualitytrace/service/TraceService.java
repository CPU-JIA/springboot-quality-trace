package com.xinghui.qualitytrace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinghui.qualitytrace.common.exception.BusinessException;
import com.xinghui.qualitytrace.dto.trace.TraceDownstreamRow;
import com.xinghui.qualitytrace.dto.trace.TraceUpstreamRow;
import com.xinghui.qualitytrace.entity.Batch;
import com.xinghui.qualitytrace.mapper.BatchMapper;
import com.xinghui.qualitytrace.mapper.TraceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 追溯服务 —— 双向追溯查询入口（F6-2/F6-3）
 */
@Service
@RequiredArgsConstructor
public class TraceService {

    private final TraceMapper traceMapper;
    private final BatchMapper batchMapper;

    /** 根据批次ID反向追溯上游来源。 */
    public List<TraceUpstreamRow> upstream(Long batchId) {
        requireBatch(batchId);
        return traceMapper.upstream(batchId);
    }

    /** 根据批次ID正向追溯下游去向。 */
    public List<TraceDownstreamRow> downstream(Long batchId) {
        requireBatch(batchId);
        return traceMapper.downstream(batchId);
    }

    /** 按批次号定位批次，供前端追溯搜索框使用。 */
    public Batch byNo(String batchNo) {
        Batch batch = batchMapper.selectOne(new LambdaQueryWrapper<Batch>()
                .eq(Batch::getBatchNo, batchNo));
        if (batch == null) {
            throw new BusinessException("批次号不存在：" + batchNo);
        }
        return batch;
    }

    private void requireBatch(Long batchId) {
        if (batchMapper.selectById(batchId) == null) {
            throw new BusinessException("批次不存在或已被删除");
        }
    }
}
