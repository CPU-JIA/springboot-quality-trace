package com.xinghui.qualitytrace.controller;

import com.xinghui.qualitytrace.common.result.Result;
import com.xinghui.qualitytrace.dto.bom.BomResponse;
import com.xinghui.qualitytrace.dto.bom.BomTreeNode;
import com.xinghui.qualitytrace.entity.Bom;
import com.xinghui.qualitytrace.security.RequireRole;
import com.xinghui.qualitytrace.service.BomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * BOM 管理接口（F2-2）—— 维护物料父子构成关系，新增时应用层 DFS 防环
 */
@Tag(name = "08-BOM管理", description = "物料清单维护与构成树查询")
@RestController
@RequestMapping("/api/boms")
@RequiredArgsConstructor
public class BomController {

    private final BomService bomService;

    @Operation(summary = "查询BOM行", description = "parentMaterialId 为空返回全部；不为空返回该父项直接子项")
    @GetMapping
    public Result<List<BomResponse>> list(@RequestParam(required = false) Long parentMaterialId) {
        return Result.ok(bomService.list(parentMaterialId));
    }

    @Operation(summary = "查询BOM构成树", description = "从指定物料向下递归展开全部子项")
    @GetMapping("/tree/{materialId}")
    public Result<BomTreeNode> tree(@PathVariable Long materialId) {
        return Result.ok(bomService.treeOf(materialId));
    }

    @Operation(summary = "新增BOM行", description = "父项必须为半成品/成品，子项必须为原材料/半成品，且不能成环")
    @RequireRole("ADMIN")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody Bom bom) {
        bomService.create(bom);
        return Result.ok();
    }

    @Operation(summary = "删除BOM行")
    @RequireRole("ADMIN")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        bomService.delete(id);
        return Result.ok();
    }
}
