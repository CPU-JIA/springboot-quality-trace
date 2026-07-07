package com.xinghui.qualitytrace.controller;

import com.xinghui.qualitytrace.common.enums.RecallLevel;
import com.xinghui.qualitytrace.common.enums.RecallStatus;
import com.xinghui.qualitytrace.common.result.PageResult;
import com.xinghui.qualitytrace.common.result.Result;
import com.xinghui.qualitytrace.dto.recall.RecallCreateRequest;
import com.xinghui.qualitytrace.dto.recall.RecallDetailUpdateRequest;
import com.xinghui.qualitytrace.dto.recall.RecallOrderResponse;
import com.xinghui.qualitytrace.entity.RecallOrder;
import com.xinghui.qualitytrace.security.RequireRole;
import com.xinghui.qualitytrace.service.RecallService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 召回管理接口（F7）—— 发起召回、明细跟踪、完成归档
 */
@Tag(name = "16-召回管理", description = "基于正向追溯自动生成召回影响面")
@RestController
@RequestMapping("/api/recalls")
@RequiredArgsConstructor
public class RecallController {

    private final RecallService recallService;

    @Operation(summary = "召回单分页查询")
    @GetMapping
    public Result<PageResult<RecallOrderResponse>> page(@RequestParam(defaultValue = "1") long current,
                                                        @RequestParam(defaultValue = "10") long size,
                                                        @RequestParam(required = false) RecallStatus status,
                                                        @RequestParam(required = false) RecallLevel recallLevel,
                                                        @RequestParam(required = false) String keyword) {
        return Result.ok(recallService.page(current, size, status, recallLevel, keyword));
    }

    @Operation(summary = "召回单详情")
    @GetMapping("/{id}")
    public Result<RecallOrderResponse> detail(@PathVariable Long id) {
        return Result.ok(recallService.detail(id));
    }

    @Operation(summary = "发起召回", description = "正向追溯源头批次并自动生成召回明细")
    @RequireRole("QUALITY_MANAGER")
    @PostMapping
    public Result<RecallOrder> create(@Valid @RequestBody RecallCreateRequest request) {
        return Result.ok(recallService.create(request));
    }

    @Operation(summary = "更新召回明细状态")
    @RequireRole("QUALITY_MANAGER")
    @PutMapping("/details/{detailId}")
    public Result<Void> updateDetail(@PathVariable Long detailId,
                                     @Valid @RequestBody RecallDetailUpdateRequest request) {
        recallService.updateDetail(detailId, request);
        return Result.ok();
    }

    @Operation(summary = "完成召回单", description = "全部明细达到终态后关闭召回单")
    @RequireRole("QUALITY_MANAGER")
    @PostMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id) {
        recallService.complete(id);
        return Result.ok();
    }
}
