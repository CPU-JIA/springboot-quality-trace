package com.xinghui.qualitytrace.controller;

import com.xinghui.qualitytrace.common.result.PageResult;
import com.xinghui.qualitytrace.common.result.Result;
import com.xinghui.qualitytrace.dto.shipment.ShipmentCreateRequest;
import com.xinghui.qualitytrace.dto.shipment.ShipmentResponse;
import com.xinghui.qualitytrace.entity.Shipment;
import com.xinghui.qualitytrace.security.RequireRole;
import com.xinghui.qualitytrace.service.ShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 出货管理接口（F6-1）—— 成品批次出货流向记录
 */
@Tag(name = "14-出货管理", description = "成品出货登记与出货记录查询")
@RestController
@RequestMapping("/api/shipments")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;

    @Operation(summary = "出货记录分页查询")
    @GetMapping
    public Result<PageResult<ShipmentResponse>> page(@RequestParam(defaultValue = "1") long current,
                                                     @RequestParam(defaultValue = "10") long size,
                                                     @RequestParam(required = false) Long batchId,
                                                     @RequestParam(required = false) Long customerId,
                                                     @RequestParam(required = false) String keyword) {
        return Result.ok(shipmentService.page(current, size, batchId, customerId, keyword));
    }

    @Operation(summary = "成品出货", description = "扣减合格成品批次余量并记录客户流向")
    @RequireRole("WAREHOUSE")
    @PostMapping
    public Result<Shipment> create(@Valid @RequestBody ShipmentCreateRequest request) {
        return Result.ok(shipmentService.create(request));
    }
}
