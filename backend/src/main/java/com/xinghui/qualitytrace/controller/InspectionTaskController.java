package com.xinghui.qualitytrace.controller;

import com.xinghui.qualitytrace.common.enums.InspectType;
import com.xinghui.qualitytrace.common.enums.TaskStatus;
import com.xinghui.qualitytrace.common.result.PageResult;
import com.xinghui.qualitytrace.common.result.Result;
import com.xinghui.qualitytrace.dto.inspection.InspectionRecordResponse;
import com.xinghui.qualitytrace.dto.inspection.InspectionSubmitRequest;
import com.xinghui.qualitytrace.dto.inspection.InspectionTaskResponse;
import com.xinghui.qualitytrace.entity.InspectionItem;
import com.xinghui.qualitytrace.security.RequireRole;
import com.xinghui.qualitytrace.service.InspectionTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 检验任务接口（F5-1/F5-2/F5-3）—— 任务池、领取、提交结果
 */
@Tag(name = "12-检验任务", description = "IQC/IPQC/FQC任务池与检验执行")
@RestController
@RequestMapping("/api/inspection-tasks")
@RequiredArgsConstructor
public class InspectionTaskController {

    private final InspectionTaskService taskService;

    @Operation(summary = "检验任务池分页查询")
    @GetMapping
    public Result<PageResult<InspectionTaskResponse>> page(@RequestParam(defaultValue = "1") long current,
                                                           @RequestParam(defaultValue = "10") long size,
                                                           @RequestParam(required = false) TaskStatus status,
                                                           @RequestParam(required = false) InspectType inspectType,
                                                           @RequestParam(required = false) Long batchId,
                                                           @RequestParam(required = false) Long processRecordId,
                                                           @RequestParam(required = false) String keyword) {
        return Result.ok(taskService.page(current, size, status, inspectType, batchId, processRecordId, keyword));
    }

    @Operation(summary = "领取检验任务")
    @RequireRole("INSPECTOR")
    @PostMapping("/{id}/claim")
    public Result<Void> claim(@PathVariable Long id) {
        taskService.claim(id);
        return Result.ok();
    }

    @Operation(summary = "提交检验结果")
    @RequireRole("INSPECTOR")
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id, @Valid @RequestBody InspectionSubmitRequest request) {
        taskService.submit(id, request);
        return Result.ok();
    }

    @Operation(summary = "检验明细记录")
    @GetMapping("/{id}/records")
    public Result<List<InspectionRecordResponse>> records(@PathVariable Long id) {
        return Result.ok(taskService.recordsOf(id));
    }

    @Operation(summary = "任务适用检验项目", description = "检验执行页动态表单的数据源，与提交完整性校验同源")
    @GetMapping("/{id}/items")
    public Result<List<InspectionItem>> items(@PathVariable Long id) {
        return Result.ok(taskService.itemsOf(id));
    }
}
