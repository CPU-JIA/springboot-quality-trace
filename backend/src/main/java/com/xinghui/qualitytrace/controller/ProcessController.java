package com.xinghui.qualitytrace.controller;

import com.xinghui.qualitytrace.common.result.PageResult;
import com.xinghui.qualitytrace.common.result.Result;
import com.xinghui.qualitytrace.entity.ProcessDef;
import com.xinghui.qualitytrace.security.RequireRole;
import com.xinghui.qualitytrace.service.ProcessService;
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

import java.util.List;

/**
 * 工序定义接口（F2-3 的工序字典部分）—— 写操作仅管理员；查询登录即可
 */
@Tag(name = "06-工序管理", description = "工序字典维护与路线编辑下拉数据")
@RestController
@RequestMapping("/api/processes")
@RequiredArgsConstructor
public class ProcessController {

    private final ProcessService processService;

    @Operation(summary = "分页查询工序", description = "keyword 对工序编码/名称模糊匹配")
    @GetMapping
    public Result<PageResult<ProcessDef>> page(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "10") long size,
                                               @RequestParam(required = false) String keyword) {
        return Result.ok(processService.page(current, size, keyword));
    }

    @Operation(summary = "工序全量列表", description = "工艺路线编辑器的下拉数据源")
    @GetMapping("/all")
    public Result<List<ProcessDef>> listAll() {
        return Result.ok(processService.listAll());
    }

    @Operation(summary = "新增工序")
    @RequireRole("ADMIN")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody ProcessDef processDef) {
        processService.create(processDef);
        return Result.ok();
    }

    @Operation(summary = "编辑工序", description = "若开启IPQC检验点，会校验该工序不是任何路线末道")
    @RequireRole("ADMIN")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ProcessDef processDef) {
        processService.update(id, processDef);
        return Result.ok();
    }

    @Operation(summary = "删除工序", description = "被路线/工序记录/检验项目引用时会被拒绝")
    @RequireRole("ADMIN")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        processService.delete(id);
        return Result.ok();
    }
}
