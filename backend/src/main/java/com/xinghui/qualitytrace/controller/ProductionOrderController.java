package com.xinghui.qualitytrace.controller;

import com.xinghui.qualitytrace.common.enums.OrderStatus;
import com.xinghui.qualitytrace.common.result.PageResult;
import com.xinghui.qualitytrace.common.result.Result;
import com.xinghui.qualitytrace.dto.production.MaterialIssueRequest;
import com.xinghui.qualitytrace.dto.production.OrderCompleteRequest;
import com.xinghui.qualitytrace.dto.production.ProcessCompleteRequest;
import com.xinghui.qualitytrace.dto.production.ProductionOrderCreateRequest;
import com.xinghui.qualitytrace.dto.production.ProductionOrderDetailResponse;
import com.xinghui.qualitytrace.dto.production.ProductionOrderResponse;
import com.xinghui.qualitytrace.entity.Batch;
import com.xinghui.qualitytrace.entity.ProductionOrder;
import com.xinghui.qualitytrace.security.RequireRole;
import com.xinghui.qualitytrace.service.ProductionOrderService;
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
 * 生产工单接口（F4）—— 创建工单、领料、工序流转、完工入库
 */
@Tag(name = "11-生产工单", description = "生产工单与工序流转")
@RestController
@RequestMapping("/api/production-orders")
@RequiredArgsConstructor
public class ProductionOrderController {

    private final ProductionOrderService orderService;

    @Operation(summary = "分页查询工单")
    @GetMapping
    public Result<PageResult<ProductionOrderResponse>> page(@RequestParam(defaultValue = "1") long current,
                                                            @RequestParam(defaultValue = "10") long size,
                                                            @RequestParam(required = false) String keyword,
                                                            @RequestParam(required = false) OrderStatus status,
                                                            @RequestParam(required = false) Long materialId) {
        return Result.ok(orderService.page(current, size, keyword, status, materialId));
    }

    @Operation(summary = "工单详情")
    @GetMapping("/{id}")
    public Result<ProductionOrderDetailResponse> detail(@PathVariable Long id) {
        return Result.ok(orderService.detail(id));
    }

    @Operation(summary = "创建生产工单", description = "按当前工艺路线生成工序执行快照")
    @RequireRole("PRODUCTION")
    @PostMapping
    public Result<ProductionOrder> create(@Valid @RequestBody ProductionOrderCreateRequest request) {
        return Result.ok(orderService.create(request));
    }

    @Operation(summary = "生产领料", description = "扣减合格在库批次余量并写入批次消耗关系")
    @RequireRole("PRODUCTION")
    @PostMapping("/{id}/issue-materials")
    public Result<Void> issueMaterials(@PathVariable Long id, @Valid @RequestBody MaterialIssueRequest request) {
        orderService.issueMaterials(id, request);
        return Result.ok();
    }

    @Operation(summary = "工序开工", description = "前道工序及其IPQC通过后方可开工")
    @RequireRole("PRODUCTION")
    @PostMapping("/process-records/{processRecordId}/start")
    public Result<Void> startProcess(@PathVariable Long processRecordId) {
        orderService.startProcess(processRecordId);
        return Result.ok();
    }

    @Operation(summary = "工序报完工", description = "IPQC检验点工序完工后自动生成过程检验任务")
    @RequireRole("PRODUCTION")
    @PostMapping("/process-records/{processRecordId}/complete")
    public Result<Void> completeProcess(@PathVariable Long processRecordId,
                                        @Valid @RequestBody ProcessCompleteRequest request) {
        orderService.completeProcess(processRecordId, request);
        return Result.ok();
    }

    @Operation(summary = "工单完工入库", description = "生成生产批次并自动创建FQC检验任务")
    @RequireRole("PRODUCTION")
    @PostMapping("/{id}/complete")
    public Result<Batch> completeOrder(@PathVariable Long id, @Valid @RequestBody OrderCompleteRequest request) {
        return Result.ok(orderService.completeOrder(id, request));
    }
}
