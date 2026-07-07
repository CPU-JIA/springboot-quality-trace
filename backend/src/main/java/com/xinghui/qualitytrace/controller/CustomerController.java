package com.xinghui.qualitytrace.controller;

import com.xinghui.qualitytrace.common.result.PageResult;
import com.xinghui.qualitytrace.common.result.Result;
import com.xinghui.qualitytrace.entity.Customer;
import com.xinghui.qualitytrace.security.RequireRole;
import com.xinghui.qualitytrace.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 客户管理接口（F2-6）—— 写操作仅管理员；查询登录即可
 */
@Tag(name = "05-客户管理", description = "客户档案维护")
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @Operation(summary = "分页查询客户")
    @GetMapping
    public Result<PageResult<Customer>> page(@RequestParam(defaultValue = "1") long current,
                                             @RequestParam(defaultValue = "10") long size,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Integer status) {
        return Result.ok(customerService.page(current, size, keyword, status));
    }

    @Operation(summary = "新增客户")
    @RequireRole("ADMIN")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody Customer customer) {
        customerService.create(customer);
        return Result.ok();
    }

    @Operation(summary = "编辑客户")
    @RequireRole("ADMIN")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody Customer customer) {
        customerService.update(id, customer);
        return Result.ok();
    }

    @Operation(summary = "删除客户", description = "有出货记录时被拒绝（改用停用）")
    @RequireRole("ADMIN")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return Result.ok();
    }
}
