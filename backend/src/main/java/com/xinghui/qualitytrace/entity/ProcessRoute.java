package com.xinghui.qualitytrace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工艺路线实体 —— 对应表 process_route（物料-工序 M:N 带序联系的物理承载）
 *
 * <p>语义：某物料（半成品/成品）的生产依次经过哪些工序，step_no 决定执行顺序。
 * 维护方式为"整条替换"（PUT），故本实体不做单行表单校验，由 RouteSaveRequest 承担。</p>
 */
@Data
@TableName("process_route")
public class ProcessRoute {

    /** 路线行ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 物料ID（被生产的半成品/成品） */
    private Long materialId;

    /** 工序ID */
    private Long processDefId;

    /** 步骤序号（从 1 连续递增） */
    private Integer stepNo;

    /** 创建时间（数据库维护） */
    private LocalDateTime createdAt;
}
