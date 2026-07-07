package com.xinghui.qualitytrace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xinghui.qualitytrace.common.enums.ProcessStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工序执行记录实体 —— 对应表 process_record（依附生产工单的弱实体）
 *
 * <p>工单创建时按当时的工艺路线生成快照，后续路线调整不影响历史工单。</p>
 */
@Data
@TableName("process_record")
public class ProcessRecord {

    /** 工序记录ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属生产工单ID */
    private Long productionOrderId;

    /** 工序定义ID */
    private Long processDefId;

    /** 步骤序号 */
    private Integer stepNo;

    /** 执行状态 */
    private ProcessStatus status;

    /** 操作员ID */
    private Long operatorId;

    /** 开工时间 */
    private LocalDateTime startTime;

    /** 完工时间 */
    private LocalDateTime endTime;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
