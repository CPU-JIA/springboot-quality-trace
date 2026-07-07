package com.xinghui.qualitytrace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工序定义实体 —— 对应表 process_def（全局工序字典：注塑/总装/整机测试…）
 */
@Data
@TableName("process_def")
public class ProcessDef {

    /** 工序ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工序编码（如 PR-ASSY，唯一） */
    @NotBlank(message = "工序编码不能为空")
    private String processCode;

    /** 工序名称 */
    @NotBlank(message = "工序名称不能为空")
    private String processName;

    /**
     * 是否 IPQC 检验点：1=该工序报完工时自动生成过程检验任务 0=否。
     * 业务约束（审计 HIGH 修复）：工艺路线的末道工序禁止设为检验点——
     * 否则完工入库会先于 IPQC 结论发生
     */
    @NotNull(message = "是否IPQC检验点不能为空")
    @Min(value = 0, message = "是否IPQC检验点只能取0或1")
    @Max(value = 1, message = "是否IPQC检验点只能取0或1")
    private Integer needIpqc;

    /** 作业内容说明 */
    private String description;

    /** 创建时间（数据库维护） */
    private LocalDateTime createdAt;
}
