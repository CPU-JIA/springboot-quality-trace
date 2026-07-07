package com.xinghui.qualitytrace.dto.bom;

import com.xinghui.qualitytrace.common.enums.MaterialCategory;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * BOM 树节点 —— 以物料为节点、BOM 行为边，递归展示"某物料由哪些子项构成"
 */
@Getter
@Builder
public class BomTreeNode {

    /** 连接到本节点的 BOM 行ID；根节点无来源行，故为 null */
    private final Long bomId;

    /** 物料ID */
    private final Long materialId;

    /** 物料编码 */
    private final String materialCode;

    /** 物料名称 */
    private final String materialName;

    /** 物料类别 */
    private final MaterialCategory category;

    /** 父项生产 1 单位时，本节点作为子项所需用量；根节点为 null */
    private final BigDecimal quantity;

    /** 子项列表 */
    private final List<BomTreeNode> children;
}
