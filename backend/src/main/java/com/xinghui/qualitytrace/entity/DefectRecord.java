package com.xinghui.qualitytrace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xinghui.qualitytrace.common.enums.DefectType;
import com.xinghui.qualitytrace.common.enums.HandleMethod;
import com.xinghui.qualitytrace.common.enums.HandleStatus;
import com.xinghui.qualitytrace.common.enums.Severity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 缺陷记录实体 —— 对应表 defect_record（检验发现或售后反馈的质量缺陷）
 *
 * <p>缺陷载体为双轨：IQC/FQC/售后缺陷挂批次；IPQC 过程缺陷挂工序执行记录。
 * 数据库 CHECK 保证二者至少其一非空，Service 层负责更友好的互斥与一致性校验。</p>
 */
@Data
@TableName("defect_record")
public class DefectRecord {

    /** 缺陷记录ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 缺陷编号（DF-yyyyMMdd-3位序号） */
    private String defectNo;

    /** 所属批次ID */
    private Long batchId;

    /** 所属工序执行记录ID */
    private Long processRecordId;

    /** 发现于检验单ID（人工登记可为空） */
    private Long inspectionTaskId;

    /** 缺陷类型 */
    private DefectType defectType;

    /** 严重度 */
    private Severity severity;

    /** 缺陷数量 */
    private BigDecimal quantity;

    /** 缺陷描述 */
    private String description;

    /** 处置方式 */
    private HandleMethod handleMethod;

    /** 处置状态 */
    private HandleStatus handleStatus;

    /** 处置人ID */
    private Long handlerId;

    /** 处置时间 */
    private LocalDateTime handledAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
