package com.xinghui.qualitytrace.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.Set;

/**
 * 批次状态枚举（8 态状态机）—— 全系统最核心的状态机
 *
 * <p>转移表与 docs/02-数据库设计.md §5.6 状态图严格一致，是三层防线的应用层实现：
 * <ol>
 *   <li>应用层：本枚举 {@link #canTransitionTo} 在 Service 写库前校验；</li>
 *   <li>数据库 CHECK：拒绝 8 值之外的任何状态字面量；</li>
 *   <li>数据库触发器：检验不合格自动冻结（带状态白名单，防终态降级）。</li>
 * </ol></p>
 *
 * <p>RECALLED 的优先级语义（§5.6）：召回影响面按追溯消耗边计算、不看批次当前状态，
 * 故 RECALLED 可从任意【非归档终态】进入（质量安全优先级最高）；被召回后仅允许
 * 流向 SCRAPPED / RETURNED 两个归档终态，绝不允许回到可用库存。</p>
 */
@Getter
@RequiredArgsConstructor
public enum BatchStatus {

    /** 待检：入库/完工后的初始状态，等待检验任务被领取 */
    PENDING_INSPECT("待检"),

    /** 检验中：检验员已领取对应检验任务 */
    INSPECTING("检验中"),

    /** 合格在库：检验结论为合格/让步接收，可供领料或出货 */
    QUALIFIED("合格在库"),

    /** 冻结：检验结论不合格，待质量主管处置评审 */
    FROZEN("冻结"),

    /** 已报废：处置结论为报废（归档终态） */
    SCRAPPED("已报废"),

    /** 已退货：处置结论为退货给供应商（归档终态，仅原料批次） */
    RETURNED("已退货"),

    /** 已耗尽：剩余量扣减至 0，生命周期正常结束 */
    DEPLETED("已耗尽"),

    /** 已召回：被召回单涉及，冻结一切流转（仅可归档为报废/退货） */
    RECALLED("已召回");

    /** 中文名称：用于校验失败提示与日志（前端展示由前端映射表负责） */
    private final String label;

    /**
     * 状态机转移表：key 可流转至 value 集合中的状态。
     * Why 静态表而非 switch：转移关系一目了然、可与设计文档逐行核对，新增转移只改一处。
     */
    private static final Map<BatchStatus, Set<BatchStatus>> TRANSITIONS = Map.of(
            PENDING_INSPECT, Set.of(INSPECTING, FROZEN, RECALLED),
            INSPECTING, Set.of(QUALIFIED, FROZEN, RECALLED),
            // 冻结后的四种处置去向 + 可被召回
            FROZEN, Set.of(PENDING_INSPECT, SCRAPPED, RETURNED, QUALIFIED, RECALLED),
            QUALIFIED, Set.of(FROZEN, DEPLETED, RECALLED),
            DEPLETED, Set.of(RECALLED),
            // 归档终态：不可再流转
            SCRAPPED, Set.of(),
            RETURNED, Set.of(),
            // 已召回批次仅可归档（报废/退供应商索赔），绝不回可用库存
            RECALLED, Set.of(SCRAPPED, RETURNED)
    );

    /** 判断能否从当前状态流转到目标状态 */
    public boolean canTransitionTo(BatchStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    /**
     * 校验流转合法性，非法即抛业务异常（Service 层写库前的统一入口）。
     *
     * @param target 目标状态
     * @throws com.xinghui.qualitytrace.common.exception.BusinessException 流转非法时
     */
    public void checkTransitionTo(BatchStatus target) {
        if (!canTransitionTo(target)) {
            throw new com.xinghui.qualitytrace.common.exception.BusinessException(
                    "批次状态不允许该操作：当前[" + label + "]不可流转为[" + target.label + "]");
        }
    }
}
