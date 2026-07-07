package com.xinghui.qualitytrace.dto.bom;

import com.xinghui.qualitytrace.common.enums.MaterialCategory;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * BOM 行响应体 —— 单行 BOM + 父/子物料信息装配，前端表格无需二次查物料字典
 */
@Getter
@Builder
public class BomResponse {

    /** BOM 行ID */
    private final Long id;

    /** 父项物料ID */
    private final Long parentMaterialId;

    /** 父项物料编码 */
    private final String parentMaterialCode;

    /** 父项物料名称 */
    private final String parentMaterialName;

    /** 父项物料类别 */
    private final MaterialCategory parentCategory;

    /** 子项物料ID */
    private final Long childMaterialId;

    /** 子项物料编码 */
    private final String childMaterialCode;

    /** 子项物料名称 */
    private final String childMaterialName;

    /** 子项物料类别 */
    private final MaterialCategory childCategory;

    /** 单位用量 */
    private final BigDecimal quantity;

    /** 创建时间 */
    private final LocalDateTime createdAt;
}
