package com.xinghui.qualitytrace.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinghui.qualitytrace.common.enums.MaterialCategory;
import com.xinghui.qualitytrace.common.exception.BusinessException;
import com.xinghui.qualitytrace.common.result.PageBounds;
import com.xinghui.qualitytrace.common.result.PageResult;
import com.xinghui.qualitytrace.dto.route.RouteSaveRequest;
import com.xinghui.qualitytrace.dto.route.RouteStepResponse;
import com.xinghui.qualitytrace.entity.Material;
import com.xinghui.qualitytrace.entity.ProcessDef;
import com.xinghui.qualitytrace.entity.ProcessRoute;
import com.xinghui.qualitytrace.mapper.MaterialMapper;
import com.xinghui.qualitytrace.mapper.ProcessDefMapper;
import com.xinghui.qualitytrace.mapper.ProcessRecordMapper;
import com.xinghui.qualitytrace.mapper.ProcessRouteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工序与工艺路线服务（F2-3）
 *
 * <p>路线保存的三重校验（含审计 HIGH 修复的配套禁令）：
 * <ol>
 *   <li>物料必须为 SEMI/PRODUCT（原材料是外购的，没有生产工艺）；</li>
 *   <li>stepNo 必须从 1 连续递增且无重复（路线是全序）；</li>
 *   <li>★末道工序禁止为 IPQC 检验点（needIpqc=1）——否则"完工入库"会先于
 *       末道 IPQC 结论发生，成品批次在过程检验通过前即已生成，IPQC 门禁失效。</li>
 * </ol></p>
 */
@Service
@RequiredArgsConstructor
public class ProcessService {

    private final ProcessDefMapper processDefMapper;
    private final ProcessRouteMapper routeMapper;
    private final MaterialMapper materialMapper;
    private final ProcessRecordMapper processRecordMapper;

    // ---------------- 工序字典 CRUD ----------------

    /** 分页查询工序（keyword 模糊匹配编码/名称） */
    public PageResult<ProcessDef> page(long current, long size, String keyword) {
        Page<ProcessDef> page = processDefMapper.selectPage(PageBounds.page(current, size),
                new LambdaQueryWrapper<ProcessDef>()
                        .and(StrUtil.isNotBlank(keyword), w -> w
                                .like(ProcessDef::getProcessCode, keyword)
                                .or().like(ProcessDef::getProcessName, keyword))
                        .orderByAsc(ProcessDef::getId));
        return PageResult.of(page);
    }

    /** 工序全量列表（路线编辑器的下拉数据源） */
    public List<ProcessDef> listAll() {
        return processDefMapper.selectList(
                new LambdaQueryWrapper<ProcessDef>().orderByAsc(ProcessDef::getId));
    }

    /** 新增工序 */
    public void create(ProcessDef processDef) {
        processDef.setId(null);
        processDef.setCreatedAt(null);
        processDefMapper.insert(processDef);
    }

    /**
     * 编辑工序。防线：
     * <ol>
     *   <li>needIpqc 发生变化时，若仍有未关闭工单引用该工序，拒绝修改，避免在制工单
     *       的过程检验门禁被主数据配置漂移改变；</li>
     *   <li>将 needIpqc 从 0 改 1 时，还须校验它不是任何路线的末道（禁令保持不变量成立）。</li>
     * </ol>
     */
    public void update(Long id, ProcessDef processDef) {
        ProcessDef existing = requireProcessDef(id);
        if (!existing.getNeedIpqc().equals(processDef.getNeedIpqc())) {
            ensureNoOpenOrderReferences(id);
        }
        // 0→1 打开检验点：校验该工序未处于任何路线的末道位置
        if (existing.getNeedIpqc() == 0 && processDef.getNeedIpqc() == 1
                && isLastStepOfAnyRoute(id)) {
            throw new BusinessException("该工序是某条工艺路线的末道工序，不可设为IPQC检验点"
                    + "（末道检验点会使完工入库先于过程检验结论，请调整路线或另设工序）");
        }
        processDef.setId(id);
        processDef.setCreatedAt(null);
        processDefMapper.updateById(processDef);
    }

    /** 删除工序（被路线/工序记录/检验项目引用时被 RESTRICT 拒绝） */
    public void delete(Long id) {
        requireProcessDef(id);
        processDefMapper.deleteById(id);
    }

    // ---------------- 工艺路线 ----------------

    /** 查询某物料的工艺路线（按步骤序装配工序信息） */
    public List<RouteStepResponse> routeOf(Long materialId) {
        requireRouteMaterial(materialId);
        List<ProcessRoute> rows = routeMapper.selectList(new LambdaQueryWrapper<ProcessRoute>()
                .eq(ProcessRoute::getMaterialId, materialId)
                .orderByAsc(ProcessRoute::getStepNo));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, ProcessDef> defById = processDefMapper.selectByIds(
                        rows.stream().map(ProcessRoute::getProcessDefId).distinct().toList())
                .stream().collect(Collectors.toMap(ProcessDef::getId, Function.identity()));
        return rows.stream().map(r -> {
            ProcessDef def = defById.get(r.getProcessDefId());
            return RouteStepResponse.builder()
                    .stepNo(r.getStepNo())
                    .processDefId(r.getProcessDefId())
                    .processCode(def.getProcessCode())
                    .processName(def.getProcessName())
                    .needIpqc(def.getNeedIpqc())
                    .build();
        }).toList();
    }

    /**
     * 整条替换保存工艺路线（事务：删旧 + 批量插新，原子提交）
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveRoute(RouteSaveRequest request) {
        // 校验 1：物料存在且可生产（SEMI/PRODUCT）
        Material material = requireRouteMaterial(request.getMaterialId());
        if (material.getStatus() == null || material.getStatus() != 1) {
            throw new BusinessException("该物料已停用，不可维护工艺路线");
        }

        // 校验 2：stepNo 从 1 连续递增且无重复
        List<RouteSaveRequest.RouteStep> steps = request.getSteps().stream()
                .sorted(Comparator.comparing(RouteSaveRequest.RouteStep::getStepNo))
                .toList();
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).getStepNo() != i + 1) {
                throw new BusinessException("步骤序号必须从 1 连续递增且不重复");
            }
        }
        Set<Long> usedProcessIds = new HashSet<>();
        for (RouteSaveRequest.RouteStep step : steps) {
            if (!usedProcessIds.add(step.getProcessDefId())) {
                throw new BusinessException("同一条工艺路线中不应重复选择同一工序");
            }
        }

        // 校验 3：工序全部存在
        List<Long> defIds = steps.stream().map(RouteSaveRequest.RouteStep::getProcessDefId).toList();
        Map<Long, ProcessDef> defById = processDefMapper.selectByIds(defIds).stream()
                .collect(Collectors.toMap(ProcessDef::getId, Function.identity()));
        if (defById.size() != defIds.stream().distinct().count()) {
            throw new BusinessException("包含不存在的工序");
        }

        // 校验 4（★HIGH 门禁配套）：末道工序禁止为 IPQC 检验点
        ProcessDef lastStep = defById.get(steps.get(steps.size() - 1).getProcessDefId());
        if (lastStep.getNeedIpqc() == 1) {
            throw new BusinessException("末道工序[" + lastStep.getProcessName()
                    + "]不可为IPQC检验点：完工入库会先于过程检验结论发生。"
                    + "请将检验点前移或以FQC承担末端把关");
        }

        // 整条替换：删旧插新
        routeMapper.delete(new LambdaQueryWrapper<ProcessRoute>()
                .eq(ProcessRoute::getMaterialId, request.getMaterialId()));
        for (RouteSaveRequest.RouteStep step : steps) {
            ProcessRoute row = new ProcessRoute();
            row.setMaterialId(request.getMaterialId());
            row.setProcessDefId(step.getProcessDefId());
            row.setStepNo(step.getStepNo());
            routeMapper.insert(row);
        }
    }

    /** 判断工序是否处于任何一条路线的末道位置 */
    private ProcessDef requireProcessDef(Long id) {
        ProcessDef processDef = processDefMapper.selectById(id);
        if (processDef == null) {
            throw new BusinessException("工序不存在或已被删除");
        }
        return processDef;
    }

    private Material requireRouteMaterial(Long materialId) {
        Material material = materialMapper.selectById(materialId);
        if (material == null) {
            throw new BusinessException("物料不存在或已被删除");
        }
        if (material.getCategory() == MaterialCategory.RAW) {
            throw new BusinessException("原材料为外购物料，不可配置生产工艺路线");
        }
        return material;
    }

    private boolean isLastStepOfAnyRoute(Long processDefId) {
        // 取该工序出现的全部路线行，逐一比对是否为所在物料路线的最大步骤号
        List<ProcessRoute> rows = routeMapper.selectList(new LambdaQueryWrapper<ProcessRoute>()
                .eq(ProcessRoute::getProcessDefId, processDefId));
        for (ProcessRoute row : rows) {
            Long maxStep = routeMapper.selectList(new LambdaQueryWrapper<ProcessRoute>()
                            .eq(ProcessRoute::getMaterialId, row.getMaterialId()))
                    .stream().map(ProcessRoute::getStepNo).max(Integer::compareTo).orElse(0).longValue();
            if (row.getStepNo().longValue() == maxStep) {
                return true;
            }
        }
        return false;
    }

    private void ensureNoOpenOrderReferences(Long processDefId) {
        Long references = processRecordMapper.countOpenOrderReferences(processDefId);
        if (references != null && references > 0) {
            throw new BusinessException("该工序已被未关闭生产工单引用，不能修改IPQC检验点开关");
        }
    }
}
