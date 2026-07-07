package com.xinghui.qualitytrace.controller;

import com.xinghui.qualitytrace.common.enums.BatchStatus;
import com.xinghui.qualitytrace.common.enums.MaterialCategory;
import com.xinghui.qualitytrace.common.enums.SourceType;
import com.xinghui.qualitytrace.common.result.PageResult;
import com.xinghui.qualitytrace.common.result.Result;
import com.xinghui.qualitytrace.dto.batch.BatchDetailResponse;
import com.xinghui.qualitytrace.dto.batch.PurchaseInboundRequest;
import com.xinghui.qualitytrace.entity.Batch;
import com.xinghui.qualitytrace.entity.BatchOverview;
import com.xinghui.qualitytrace.security.RequireRole;
import com.xinghui.qualitytrace.service.BatchService;
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

/**
 * 批次接口（F3-1/F3-2）—— 原材料入库与批次台账
 */
@Tag(name = "10-批次管理", description = "原材料入库、批次台账、批次详情")
@RestController
@RequestMapping("/api/batches")
@RequiredArgsConstructor
public class BatchController {

    private final BatchService batchService;

    @Operation(summary = "批次台账分页查询")
    @GetMapping
    public Result<PageResult<BatchOverview>> page(@RequestParam(defaultValue = "1") long current,
                                                  @RequestParam(defaultValue = "10") long size,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) BatchStatus status,
                                                  @RequestParam(required = false) SourceType sourceType,
                                                  @RequestParam(required = false) MaterialCategory materialCategory,
                                                  @RequestParam(required = false) Long materialId) {
        return Result.ok(batchService.page(current, size, keyword, status, sourceType, materialCategory, materialId));
    }

    @Operation(summary = "批次详情", description = "返回批次主信息、全景投影与检验任务历史")
    @GetMapping("/{id}")
    public Result<BatchDetailResponse> detail(@PathVariable Long id) {
        return Result.ok(batchService.detail(id));
    }

    @Operation(summary = "原材料入库", description = "创建采购批次并自动生成 IQC 检验任务")
    @RequireRole("WAREHOUSE")
    @PostMapping("/purchase-inbound")
    public Result<Batch> purchaseInbound(@Valid @RequestBody PurchaseInboundRequest request) {
        return Result.ok(batchService.purchaseInbound(request));
    }
}
