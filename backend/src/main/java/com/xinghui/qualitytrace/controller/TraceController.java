package com.xinghui.qualitytrace.controller;

import com.xinghui.qualitytrace.common.result.Result;
import com.xinghui.qualitytrace.dto.trace.TraceDownstreamRow;
import com.xinghui.qualitytrace.dto.trace.TraceUpstreamRow;
import com.xinghui.qualitytrace.entity.Batch;
import com.xinghui.qualitytrace.service.TraceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 追溯查询接口（F6-2/F6-3）—— 批次级双向追溯
 */
@Tag(name = "15-追溯查询", description = "递归CTE实现批次上游/下游追溯")
@RestController
@RequestMapping("/api/trace")
@RequiredArgsConstructor
public class TraceController {

    private final TraceService traceService;

    @Operation(summary = "反向追溯", description = "输入批次ID，展开其全部上游来源")
    @GetMapping("/upstream/{batchId}")
    public Result<List<TraceUpstreamRow>> upstream(@PathVariable Long batchId) {
        return Result.ok(traceService.upstream(batchId));
    }

    @Operation(summary = "正向追溯", description = "输入批次ID，展开其全部下游去向与出货客户")
    @GetMapping("/downstream/{batchId}")
    public Result<List<TraceDownstreamRow>> downstream(@PathVariable Long batchId) {
        return Result.ok(traceService.downstream(batchId));
    }

    @Operation(summary = "按批次号查询批次", description = "追溯搜索框先将批次号解析为批次ID")
    @GetMapping("/by-no/{batchNo}")
    public Result<Batch> byNo(@PathVariable String batchNo) {
        return Result.ok(traceService.byNo(batchNo));
    }
}
