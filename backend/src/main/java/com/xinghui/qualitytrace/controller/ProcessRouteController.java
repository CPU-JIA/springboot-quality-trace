package com.xinghui.qualitytrace.controller;

import com.xinghui.qualitytrace.common.result.Result;
import com.xinghui.qualitytrace.dto.route.RouteSaveRequest;
import com.xinghui.qualitytrace.dto.route.RouteStepResponse;
import com.xinghui.qualitytrace.security.RequireRole;
import com.xinghui.qualitytrace.service.ProcessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工艺路线接口（F2-3 的路线部分）—— 整条替换保存，保证步骤序号连续且末道非 IPQC 点
 */
@Tag(name = "07-工艺路线", description = "按物料维护生产工艺路线")
@RestController
@RequestMapping("/api/process-routes")
@RequiredArgsConstructor
public class ProcessRouteController {

    private final ProcessService processService;

    @Operation(summary = "查询物料工艺路线", description = "materialId 为半成品/成品物料ID")
    @GetMapping
    public Result<List<RouteStepResponse>> routeOf(@RequestParam Long materialId) {
        return Result.ok(processService.routeOf(materialId));
    }

    @Operation(summary = "保存物料工艺路线", description = "整条替换；步骤必须从1连续递增")
    @RequireRole("ADMIN")
    @PutMapping
    public Result<Void> saveRoute(@Valid @RequestBody RouteSaveRequest request) {
        processService.saveRoute(request);
        return Result.ok();
    }
}
