package com.xinghui.qualitytrace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinghui.qualitytrace.common.enums.MaterialCategory;
import com.xinghui.qualitytrace.common.exception.BusinessException;
import com.xinghui.qualitytrace.dto.bom.BomResponse;
import com.xinghui.qualitytrace.dto.bom.BomTreeNode;
import com.xinghui.qualitytrace.entity.Bom;
import com.xinghui.qualitytrace.entity.Material;
import com.xinghui.qualitytrace.mapper.BomMapper;
import com.xinghui.qualitytrace.mapper.MaterialMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * BOM 服务 —— 物料构成关系维护（F2-2）
 *
 * <p>数据库只能用 CHECK 拦住"自己构成自己"这一层直接环；A→B→C→A 这类跨层环
 * 必须在应用层用 DFS 识别。这里在新增 BOM 行前，以"子项能否继续展开到父项"为判定：
 * 若 child 已经间接包含 parent，则新增 parent→child 会制造环，必须拒绝。</p>
 */
@Service
@RequiredArgsConstructor
public class BomService {

    private final BomMapper bomMapper;
    private final MaterialMapper materialMapper;

    /**
     * 查询 BOM 行列表。parentMaterialId 为空时返回全部；不为空时只返回某父项的直接子项。
     */
    public List<BomResponse> list(Long parentMaterialId) {
        List<Bom> rows = bomMapper.selectList(new LambdaQueryWrapper<Bom>()
                .eq(parentMaterialId != null, Bom::getParentMaterialId, parentMaterialId)
                .orderByAsc(Bom::getParentMaterialId)
                .orderByAsc(Bom::getId));
        return assemble(rows);
    }

    /**
     * 查询某物料的 BOM 构成树。根节点为该物料自身，children 为逐级子项。
     */
    public BomTreeNode treeOf(Long materialId) {
        Material root = requireMaterial(materialId);
        List<Bom> rows = bomMapper.selectList(new LambdaQueryWrapper<Bom>()
                .orderByAsc(Bom::getParentMaterialId)
                .orderByAsc(Bom::getId));
        Map<Long, List<Bom>> childrenByParent = rows.stream()
                .collect(Collectors.groupingBy(Bom::getParentMaterialId));
        Map<Long, Material> materialById = loadMaterials(rows, root);
        return buildTree(root, null, null, childrenByParent, materialById, new HashSet<>());
    }

    /**
     * 新增 BOM 行（父项 → 子项）。跨层防环、类别语义、存在性均在落库前友好校验。
     */
    @Transactional(rollbackFor = Exception.class)
    public void create(Bom bom) {
        bom.setId(null);
        bom.setCreatedAt(null);
        Material parent = requireMaterialForUpdate(bom.getParentMaterialId());
        Material child = requireMaterial(bom.getChildMaterialId());
        validateCategory(parent, child);
        validateActive(parent, child);
        ensureNoOpenOrderReferences(parent.getId());
        if (exists(parent.getId(), child.getId())) {
            throw new BusinessException("该父项物料下已存在相同子项BOM行，请勿重复新增");
        }
        if (wouldCreateCycle(parent.getId(), child.getId())) {
            throw new BusinessException("新增该BOM行会形成循环构成关系，请检查物料层级");
        }
        bomMapper.insert(bom);
    }

    /** 删除 BOM 行。若不存在，按业务错误提示而非静默成功。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Bom existing = bomMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("BOM行不存在或已被删除");
        }
        requireMaterialForUpdate(existing.getParentMaterialId());
        ensureNoOpenOrderReferences(existing.getParentMaterialId());
        bomMapper.deleteById(id);
    }

    // ---------------- 私有辅助 ----------------

    private Material requireMaterial(Long id) {
        Material material = materialMapper.selectById(id);
        if (material == null) {
            throw new BusinessException("物料不存在或已被删除");
        }
        return material;
    }

    private Material requireMaterialForUpdate(Long id) {
        Material material = materialMapper.selectByIdForUpdate(id);
        if (material == null) {
            throw new BusinessException("物料不存在或已被删除");
        }
        return material;
    }

    /**
     * BOM 类别约束：父项必须可生产（半成品/成品），子项只能是原材料或半成品。
     */
    private void validateCategory(Material parent, Material child) {
        if (parent.getId().equals(child.getId())) {
            throw new BusinessException("父项物料与子项物料不能相同");
        }
        if (parent.getCategory() == MaterialCategory.RAW) {
            throw new BusinessException("原材料为外购物料，不能作为BOM父项");
        }
        if (child.getCategory() == MaterialCategory.PRODUCT) {
            throw new BusinessException("成品不能作为BOM子项，请改用原材料或半成品");
        }
    }

    private void validateActive(Material parent, Material child) {
        if (parent.getStatus() == null || parent.getStatus() != 1) {
            throw new BusinessException("父项物料已停用，不能新增BOM行");
        }
        if (child.getStatus() == null || child.getStatus() != 1) {
            throw new BusinessException("子项物料已停用，不能新增BOM行");
        }
    }

    private boolean exists(Long parentId, Long childId) {
        Long count = bomMapper.selectCount(new LambdaQueryWrapper<Bom>()
                .eq(Bom::getParentMaterialId, parentId)
                .eq(Bom::getChildMaterialId, childId));
        return count != null && count > 0;
    }

    private void ensureNoOpenOrderReferences(Long parentMaterialId) {
        Long references = bomMapper.countOpenOrderReferences(parentMaterialId);
        if (references != null && references > 0) {
            throw new BusinessException("该BOM父项物料已存在未关闭生产工单，不能调整BOM构成");
        }
    }

    /**
     * 若 child 通过现有 BOM 子项链路能到达 parent，则新增 parent→child 会形成环。
     */
    private boolean wouldCreateCycle(Long parentId, Long childId) {
        List<Bom> rows = bomMapper.selectList(new LambdaQueryWrapper<Bom>());
        Map<Long, List<Long>> graph = new HashMap<>();
        for (Bom row : rows) {
            graph.computeIfAbsent(row.getParentMaterialId(), k -> new ArrayList<>())
                    .add(row.getChildMaterialId());
        }
        return canReach(childId, parentId, graph, new HashSet<>());
    }

    private boolean canReach(Long current, Long target, Map<Long, List<Long>> graph, Set<Long> visited) {
        if (current.equals(target)) {
            return true;
        }
        if (!visited.add(current)) {
            return false;
        }
        for (Long next : graph.getOrDefault(current, List.of())) {
            if (canReach(next, target, graph, visited)) {
                return true;
            }
        }
        return false;
    }

    /** BOM 行 → 响应体，批量装配物料信息，避免 N+1 查询。 */
    private List<BomResponse> assemble(List<Bom> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> materialIds = rows.stream()
                .flatMap(r -> List.of(r.getParentMaterialId(), r.getChildMaterialId()).stream())
                .distinct()
                .toList();
        Map<Long, Material> materialById = materialMapper.selectByIds(materialIds).stream()
                .collect(Collectors.toMap(Material::getId, Function.identity()));
        return rows.stream().map(row -> {
            Material parent = materialById.get(row.getParentMaterialId());
            Material child = materialById.get(row.getChildMaterialId());
            return BomResponse.builder()
                    .id(row.getId())
                    .parentMaterialId(row.getParentMaterialId())
                    .parentMaterialCode(parent.getMaterialCode())
                    .parentMaterialName(parent.getName())
                    .parentCategory(parent.getCategory())
                    .childMaterialId(row.getChildMaterialId())
                    .childMaterialCode(child.getMaterialCode())
                    .childMaterialName(child.getName())
                    .childCategory(child.getCategory())
                    .quantity(row.getQuantity())
                    .createdAt(row.getCreatedAt())
                    .build();
        }).toList();
    }

    private Map<Long, Material> loadMaterials(List<Bom> rows, Material root) {
        Set<Long> ids = new HashSet<>();
        ids.add(root.getId());
        for (Bom row : rows) {
            ids.add(row.getParentMaterialId());
            ids.add(row.getChildMaterialId());
        }
        return materialMapper.selectByIds(ids.stream().toList()).stream()
                .collect(Collectors.toMap(Material::getId, Function.identity()));
    }

    private BomTreeNode buildTree(Material material,
                                  Long bomId,
                                  java.math.BigDecimal quantity,
                                  Map<Long, List<Bom>> childrenByParent,
                                  Map<Long, Material> materialById,
                                  Set<Long> path) {
        if (!path.add(material.getId())) {
            // 正常数据不会进入这里；防御性截断可避免历史脏数据导致接口递归爆栈。
            return BomTreeNode.builder()
                    .bomId(bomId)
                    .materialId(material.getId())
                    .materialCode(material.getMaterialCode())
                    .materialName(material.getName())
                    .category(material.getCategory())
                    .quantity(quantity)
                    .children(List.of())
                    .build();
        }
        List<BomTreeNode> children = childrenByParent.getOrDefault(material.getId(), List.of()).stream()
                .map(row -> buildTree(materialById.get(row.getChildMaterialId()), row.getId(),
                        row.getQuantity(), childrenByParent, materialById, new HashSet<>(path)))
                .toList();
        return BomTreeNode.builder()
                .bomId(bomId)
                .materialId(material.getId())
                .materialCode(material.getMaterialCode())
                .materialName(material.getName())
                .category(material.getCategory())
                .quantity(quantity)
                .children(children)
                .build();
    }
}
