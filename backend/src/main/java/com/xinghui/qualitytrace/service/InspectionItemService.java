package com.xinghui.qualitytrace.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinghui.qualitytrace.common.enums.InspectType;
import com.xinghui.qualitytrace.common.enums.MaterialCategory;
import com.xinghui.qualitytrace.common.exception.BusinessException;
import com.xinghui.qualitytrace.common.result.PageBounds;
import com.xinghui.qualitytrace.common.result.PageResult;
import com.xinghui.qualitytrace.dto.inspection.InspectionItemResponse;
import com.xinghui.qualitytrace.entity.InspectionItem;
import com.xinghui.qualitytrace.entity.Material;
import com.xinghui.qualitytrace.entity.ProcessDef;
import com.xinghui.qualitytrace.mapper.InspectionItemMapper;
import com.xinghui.qualitytrace.mapper.MaterialMapper;
import com.xinghui.qualitytrace.mapper.ProcessDefMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 检验项目服务 —— 检验标准维护（F2-4）
 *
 * <p>本服务把数据库 CHECK 的硬错误前移成友好的业务提示：
 * IPQC 必须挂靠工序；IQC/FQC 不允许挂工序。检验类型与物料类别也在此校验，
 * 避免给原材料配置 FQC 或给成品配置 IQC 这类语义错误。</p>
 */
@Service
@RequiredArgsConstructor
public class InspectionItemService {

    private final InspectionItemMapper itemMapper;
    private final MaterialMapper materialMapper;
    private final ProcessDefMapper processDefMapper;

    /** 分页查询检验项目，支持关键字、检验类型、物料筛选。 */
    public PageResult<InspectionItemResponse> page(long current,
                                                   long size,
                                                   String keyword,
                                                   InspectType inspectType,
                                                   Long materialId) {
        Page<InspectionItem> page = itemMapper.selectPage(PageBounds.page(current, size),
                new LambdaQueryWrapper<InspectionItem>()
                        .and(StrUtil.isNotBlank(keyword), w -> w
                                .like(InspectionItem::getItemCode, keyword)
                                .or().like(InspectionItem::getItemName, keyword))
                        .eq(inspectType != null, InspectionItem::getInspectType, inspectType)
                        .eq(materialId != null, InspectionItem::getMaterialId, materialId)
                        .orderByAsc(InspectionItem::getId));
        List<InspectionItemResponse> rows = assemble(page.getRecords());
        return PageResult.of(rows, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 新增检验项目。 */
    @Transactional(rollbackFor = Exception.class)
    public void create(InspectionItem item) {
        item.setId(null);
        item.setCreatedAt(null);
        lockTargetMaterials(item);
        validate(item);
        ensureNoOpenTaskReferences(item, "新增");
        itemMapper.insert(item);
    }

    /** 编辑检验项目。 */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, InspectionItem item) {
        InspectionItem existing = requireItem(id);
        item.setId(id);
        item.setCreatedAt(null);
        lockTargetMaterials(existing, item);
        validate(item);
        ensureNoOpenTaskReferences(existing, "调整");
        ensureNoOpenTaskReferences(item, "调整");
        itemMapper.updateById(item);
    }

    /** 删除检验项目。已有检验记录引用时由外键 RESTRICT 拒绝。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        InspectionItem existing = requireItem(id);
        lockTargetMaterials(existing);
        ensureNoOpenTaskReferences(existing, "删除");
        itemMapper.deleteById(id);
    }

    // ---------------- 私有辅助 ----------------

    private InspectionItem requireItem(Long id) {
        InspectionItem item = itemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException("检验项目不存在或已被删除");
        }
        return item;
    }

    private void lockTargetMaterials(InspectionItem... items) {
        List<Long> materialIds = Arrays.stream(items)
                .filter(Objects::nonNull)
                .map(InspectionItem::getMaterialId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        for (Long materialId : materialIds) {
            Material material = materialMapper.selectByIdForUpdate(materialId);
            if (material == null) {
                throw new BusinessException("适用物料不存在");
            }
        }
    }

    private void validate(InspectionItem item) {
        Material material = materialMapper.selectById(item.getMaterialId());
        if (material == null) {
            throw new BusinessException("适用物料不存在");
        }
        validateInspectTypeAndTarget(item, material);
        validateQuantitativeRule(item);
    }

    /** 检验类型与物料/工序挂靠关系校验。 */
    private void validateInspectTypeAndTarget(InspectionItem item, Material material) {
        if (item.getInspectType() == InspectType.IQC) {
            if (material.getCategory() != MaterialCategory.RAW) {
                throw new BusinessException("IQC检验项目仅适用于原材料");
            }
            if (item.getProcessDefId() != null) {
                throw new BusinessException("IQC检验项目不应挂靠工序");
            }
            return;
        }

        if (item.getInspectType() == InspectType.FQC) {
            if (material.getCategory() == MaterialCategory.RAW) {
                throw new BusinessException("FQC检验项目仅适用于半成品或成品");
            }
            if (item.getProcessDefId() != null) {
                throw new BusinessException("FQC检验项目不应挂靠工序");
            }
            return;
        }

        if (item.getInspectType() == InspectType.IPQC) {
            if (material.getCategory() == MaterialCategory.RAW) {
                throw new BusinessException("IPQC检验项目仅适用于半成品或成品");
            }
            if (item.getProcessDefId() == null) {
                throw new BusinessException("IPQC检验项目必须挂靠工序");
            }
            if (processDefMapper.selectById(item.getProcessDefId()) == null) {
                throw new BusinessException("挂靠工序不存在");
            }
        }
    }

    /** 定量/定性项目的上下限语义校验。 */
    private void validateQuantitativeRule(InspectionItem item) {
        if (item.getIsQuantitative() == null || (item.getIsQuantitative() != 0 && item.getIsQuantitative() != 1)) {
            throw new BusinessException("是否定量只能取 1 或 0");
        }
        if (item.getIsQuantitative() == 1) {
            if (item.getLowerLimit() == null && item.getUpperLimit() == null) {
                throw new BusinessException("定量检验项目至少需要填写一个上下限");
            }
            if (item.getLowerLimit() != null && item.getUpperLimit() != null
                    && item.getLowerLimit().compareTo(item.getUpperLimit()) > 0) {
                throw new BusinessException("定量检验项目的下限不能大于上限");
            }
        } else if (item.getLowerLimit() != null || item.getUpperLimit() != null) {
            throw new BusinessException("定性检验项目不应填写上下限");
        }
    }

    private void ensureNoOpenTaskReferences(InspectionItem item, String action) {
        Long references = itemMapper.countOpenTaskReferences(
                item.getMaterialId(), item.getInspectType(), item.getProcessDefId());
        if (references != null && references > 0) {
            throw new BusinessException("该检验对象已有待检或检验中任务，不能" + action + "检验标准");
        }
    }

    /** 检验项目实体 → 响应体，批量装配物料与工序信息，避免 N+1 查询。 */
    private List<InspectionItemResponse> assemble(List<InspectionItem> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, Material> materialById = materialMapper.selectByIds(
                        rows.stream().map(InspectionItem::getMaterialId).distinct().toList())
                .stream().collect(Collectors.toMap(Material::getId, Function.identity()));
        List<Long> processIds = rows.stream()
                .map(InspectionItem::getProcessDefId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, ProcessDef> processById = processIds.isEmpty()
                ? Map.of()
                : processDefMapper.selectByIds(processIds).stream()
                .collect(Collectors.toMap(ProcessDef::getId, Function.identity()));

        return rows.stream().map(row -> {
            Material material = materialById.get(row.getMaterialId());
            ProcessDef process = row.getProcessDefId() == null ? null : processById.get(row.getProcessDefId());
            return InspectionItemResponse.builder()
                    .id(row.getId())
                    .itemCode(row.getItemCode())
                    .itemName(row.getItemName())
                    .inspectType(row.getInspectType())
                    .materialId(row.getMaterialId())
                    .materialCode(material.getMaterialCode())
                    .materialName(material.getName())
                    .processDefId(row.getProcessDefId())
                    .processCode(process == null ? null : process.getProcessCode())
                    .processName(process == null ? null : process.getProcessName())
                    .isQuantitative(row.getIsQuantitative())
                    .standardDesc(row.getStandardDesc())
                    .lowerLimit(row.getLowerLimit())
                    .upperLimit(row.getUpperLimit())
                    .unit(row.getUnit())
                    .createdAt(row.getCreatedAt())
                    .build();
        }).toList();
    }
}
