package com.xinghui.qualitytrace.controller;

import com.xinghui.qualitytrace.common.enums.MaterialCategory;
import com.xinghui.qualitytrace.common.result.PageResult;
import com.xinghui.qualitytrace.common.result.Result;
import com.xinghui.qualitytrace.dto.common.StatusRequest;
import com.xinghui.qualitytrace.entity.Material;
import com.xinghui.qualitytrace.security.RequireRole;
import com.xinghui.qualitytrace.service.MaterialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 物料管理接口（F2-1）—— 写操作仅管理员；查询登录即可（下拉选择等场景全角色可用）
 */
@Tag(name = "03-物料管理", description = "原材料/半成品/成品统一管理")
@RestController
@RequestMapping("/api/materials")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;

    @Operation(summary = "分页查询物料")
    @GetMapping
    public Result<PageResult<Material>> page(@RequestParam(defaultValue = "1") long current,
                                             @RequestParam(defaultValue = "10") long size,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) MaterialCategory category,
                                             @RequestParam(required = false) Integer status) {
        return Result.ok(materialService.page(current, size, keyword, category, status));
    }

    @Operation(summary = "新增物料")
    @RequireRole("ADMIN")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody Material material) {
        materialService.create(material);
        return Result.ok();
    }

    @Operation(summary = "编辑物料", description = "类别不允许修改")
    @RequireRole("ADMIN")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody Material material) {
        materialService.update(id, material);
        return Result.ok();
    }

    @Operation(summary = "删除物料", description = "被批次/BOM/路线引用时会被拒绝（提示改用停用）")
    @RequireRole("ADMIN")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        materialService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "启用/停用物料", description = "停用前校验无在库批次")
    @RequireRole("ADMIN")
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        materialService.changeStatus(id, request.getStatus());
        return Result.ok();
    }
}
