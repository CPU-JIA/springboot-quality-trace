package com.xinghui.qualitytrace.common.enums;

import com.xinghui.qualitytrace.common.exception.BusinessException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 生产工单状态枚举 —— 线性状态机
 *
 * <p>流转（docs/02 §5.6）：CREATED（创建）→ IN_PROGRESS（首道工序开工）
 * → COMPLETED（末道工序完工 + 完工入库）→ CLOSED（FQC 完成后归档）。
 * 线性推进、不允许跳级与回退，故校验只需比较序数相邻性。</p>
 */
@Getter
@RequiredArgsConstructor
public enum OrderStatus {

    /** 已创建：工单与工序快照已生成，尚未开工 */
    CREATED("已创建"),

    /** 生产中：首道工序已开工 */
    IN_PROGRESS("生产中"),

    /** 已完工：全部工序完成，成品批次已生成、FQC 已下发 */
    COMPLETED("已完工"),

    /** 已关闭：FQC 出结论后归档 */
    CLOSED("已关闭");

    private final String label;

    /** 线性状态机：仅允许流转到紧邻的下一状态 */
    public void checkAdvanceTo(OrderStatus target) {
        if (target.ordinal() != this.ordinal() + 1) {
            throw new BusinessException(
                    "工单状态不允许该操作：当前[" + label + "]不可流转为[" + target.label + "]");
        }
    }
}
