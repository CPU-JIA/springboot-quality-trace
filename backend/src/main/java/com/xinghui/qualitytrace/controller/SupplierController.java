package com.xinghui.qualitytrace.controller;

import com.xinghui.qualitytrace.common.result.PageResult;
import com.xinghui.qualitytrace.common.result.Result;
import com.xinghui.qualitytrace.entity.Supplier;
import com.xinghui.qualitytrace.security.RequireRole;
import com.xinghui.qualitytrace.service.SupplierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 供应商管理接口（F2-5）—— 写操作仅管理员；查询登录即可
 */
@Tag(name = "04-供应商管理", description = "供应商档案维护")
@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @Operation(summary = "分页查询供应商")
    @GetMapping
    public Result<PageResult<Supplier>> page(@RequestParam(defaultValue = "1") long current,
                                             @RequestParam(defaultValue = "10") long size,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Integer status) {
        return Result.ok(supplierService.page(current, size, keyword, status));
    }

    @Operation(summary = "新增供应商")
    @RequireRole("ADMIN")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody Supplier supplier) {
        supplierService.create(supplier);
        return Result.ok();
    }

    @Operation(summary = "编辑供应商", description = "status 字段随表单整体提交（1合作中/0停用）")
    @RequireRole("ADMIN")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody Supplier supplier) {
        supplierService.update(id, supplier);
        return Result.ok();
    }

    @Operation(summary = "删除供应商", description = "有历史批次时被拒绝（改用停用）")
    @RequireRole("ADMIN")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        supplierService.delete(id);
        return Result.ok();
    }
}
