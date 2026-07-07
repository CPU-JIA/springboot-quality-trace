package com.xinghui.qualitytrace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 检验记录明细实体 —— 对应表 inspection_record（检验单 × 检验项目）
 *
 * <p>定量项的 isPass 是按提交时的上下限计算出的判定快照；即使后续标准调整，
 * 历史检验结果也保持当时口径，满足质量记录不可随标准漂移的审计要求。</p>
 */
@Data
@TableName("inspection_record")
public class InspectionRecord {

    /** 检验记录ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属检验单ID */
    private Long inspectionTaskId;

    /** 检验项目ID */
    private Long inspectionItemId;

    /** 实测值（定量项填写） */
    private BigDecimal measuredValue;

    /** 结果描述（定性项观察记录或定量项补充说明） */
    private String resultDesc;

    /** 单项判定：1=合格 0=不合格 */
    private Integer isPass;

    /** 检验时间 */
    private LocalDateTime inspectedAt;
}
