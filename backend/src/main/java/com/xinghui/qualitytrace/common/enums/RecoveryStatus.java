package com.xinghui.qualitytrace.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 召回明细回收状态枚举
 *
 * <p>终态判定：RECOVERED / UNRECOVERABLE 为终态——召回单须全部明细达到终态
 * 方可关闭（docs/02 §6.5 召回事务的完成前置校验）。</p>
 */
@Getter
@RequiredArgsConstructor
public enum RecoveryStatus {

    /** 待通知 */
    PENDING("待通知"),

    /** 已通知：客户已收到召回通知，货物在途 */
    NOTIFIED("已通知"),

    /** 已回收：货物已回收入隔离区（终态） */
    RECOVERED("已回收"),

    /** 无法回收：已消费/灭失等（终态，需备注说明） */
    UNRECOVERABLE("无法回收");

    private final String label;

    /** 是否终态（召回单关闭的前置条件） */
    public boolean isFinal() {
        return this == RECOVERED || this == UNRECOVERABLE;
    }

    /** 判断能否从当前回收状态按标准客户召回流程流转到目标状态。 */
    public boolean canTransitionTo(RecoveryStatus target) {
        if (this == target) {
            return true;
        }
        return switch (this) {
            case PENDING -> target == NOTIFIED;
            case NOTIFIED -> target.isFinal();
            case RECOVERED, UNRECOVERABLE -> false;
        };
    }

    /** 校验召回明细状态流转，非法时给出中文业务提示。 */
    public void checkTransitionTo(RecoveryStatus target) {
        if (!canTransitionTo(target)) {
            throw new com.xinghui.qualitytrace.common.exception.BusinessException(
                    "召回明细状态不允许该操作：当前[" + label + "]不可流转为[" + target.label + "]");
        }
    }
}
