package com.xinghui.qualitytrace.controller;

import com.xinghui.qualitytrace.common.enums.InspectType;
import com.xinghui.qualitytrace.common.result.PageResult;
import com.xinghui.qualitytrace.common.result.Result;
import com.xinghui.qualitytrace.dto.inspection.InspectionItemResponse;
import com.xinghui.qualitytrace.entity.InspectionItem;
import com.xinghui.qualitytrace.security.RequireRole;
import com.xinghui.qualitytrace.service.InspectionItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 检验项目接口（F2-4）—— 维护 IQC/IPQC/FQC 的检验标准项
 */
@Tag(name = "09-检验标准", description = "检验项目维护")
@RestController
@RequestMapping("/api/inspection-items")
@RequiredArgsConstructor
public class InspectionItemController {

    private final InspectionItemService itemService;

    @Operation(summary = "分页查询检验项目")
    @GetMapping
    public Result<PageResult<InspectionItemResponse>> page(@RequestParam(defaultValue = "1") long current,
                                                           @RequestParam(defaultValue = "10") long size,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) InspectType inspectType,
                                                           @RequestParam(required = false) Long materialId) {
        return Result.ok(itemService.page(current, size, keyword, inspectType, materialId));
    }

    @Operation(summary = "新增检验项目")
    @RequireRole("ADMIN")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody InspectionItem item) {
        itemService.create(item);
        return Result.ok();
    }

    @Operation(summary = "编辑检验项目")
    @RequireRole("ADMIN")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody InspectionItem item) {
        itemService.update(id, item);
        return Result.ok();
    }

    @Operation(summary = "删除检验项目", description = "已有检验记录引用时会被数据库外键拒绝")
    @RequireRole("ADMIN")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        itemService.delete(id);
        return Result.ok();
    }
}
