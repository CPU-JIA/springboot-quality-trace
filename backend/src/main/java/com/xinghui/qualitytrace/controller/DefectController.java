package com.xinghui.qualitytrace.controller;

import com.xinghui.qualitytrace.common.enums.DefectType;
import com.xinghui.qualitytrace.common.enums.HandleStatus;
import com.xinghui.qualitytrace.common.result.PageResult;
import com.xinghui.qualitytrace.common.result.Result;
import com.xinghui.qualitytrace.dto.defect.DefectCreateRequest;
import com.xinghui.qualitytrace.dto.defect.DefectHandleRequest;
import com.xinghui.qualitytrace.dto.defect.DefectResponse;
import com.xinghui.qualitytrace.dto.defect.ProcessDefectTargetResponse;
import com.xinghui.qualitytrace.entity.DefectRecord;
import com.xinghui.qualitytrace.security.RequireRole;
import com.xinghui.qualitytrace.service.DefectService;
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
 * 缺陷管理接口（F5-4/F5-5）—— 缺陷登记与质量主管处置
 */
@Tag(name = "13-缺陷管理", description = "缺陷登记、查询与处置")
@RestController
@RequestMapping("/api/defects")
@RequiredArgsConstructor
public class DefectController {

    private final DefectService defectService;

    @Operation(summary = "缺陷分页查询")
    @GetMapping
    public Result<PageResult<DefectResponse>> page(@RequestParam(defaultValue = "1") long current,
                                                   @RequestParam(defaultValue = "10") long size,
                                                   @RequestParam(required = false) DefectType defectType,
                                                   @RequestParam(required = false) HandleStatus handleStatus,
                                                   @RequestParam(required = false) Long batchId,
                                                   @RequestParam(required = false) Long processRecordId,
                                                   @RequestParam(required = false) String keyword) {
        return Result.ok(defectService.page(current, size, defectType, handleStatus, batchId, processRecordId, keyword));
    }

    @Operation(summary = "过程缺陷可选工序", description = "分页返回当前允许人工登记过程缺陷的进行中工序")
    @GetMapping("/process-targets")
    public Result<PageResult<ProcessDefectTargetResponse>> processTargets(@RequestParam(defaultValue = "1") long current,
                                                                          @RequestParam(defaultValue = "20") long size,
                                                                          @RequestParam(required = false) String keyword) {
        return Result.ok(defectService.processDefectTargets(current, size, keyword));
    }

    @Operation(summary = "登记缺陷", description = "质检员或质量主管可登记批次/过程缺陷")
    @RequireRole({"INSPECTOR", "QUALITY_MANAGER"})
    @PostMapping
    public Result<DefectRecord> create(@Valid @RequestBody DefectCreateRequest request) {
        return Result.ok(defectService.create(request));
    }

    @Operation(summary = "处置缺陷", description = "质量主管评审后执行返工/报废/让步/退货")
    @RequireRole("QUALITY_MANAGER")
    @PutMapping("/{id}/handle")
    public Result<Void> handle(@PathVariable Long id, @Valid @RequestBody DefectHandleRequest request) {
        defectService.handle(id, request);
        return Result.ok();
    }
}
