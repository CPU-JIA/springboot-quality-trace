package com.xinghui.qualitytrace.common.exception;

import lombok.Getter;

/**
 * 业务异常 —— 业务规则被违反时由 Service 层抛出的受控异常
 *
 * <p>典型抛出场景：批次余量不足、状态机拒绝流转、IPQC 门禁不通过、
 * 检验明细不完整、召回明细未全部终态等。统一由
 * {@link GlobalExceptionHandler} 捕获并转换为 {@code Result.fail(code, message)}。</p>
 *
 * <p>Why 用非受检异常（RuntimeException）：业务异常必须触发 Spring 事务回滚
 * （@Transactional 默认仅对 RuntimeException 回滚），且避免调用链逐层 throws 的噪音。</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务状态码：默认 400；401/403 由安全层使用 */
    private final int code;

    /** 常规业务失败（code=400），message 为面向用户的中文提示 */
    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }

    /** 指定业务码的失败（401 未认证 / 403 无权限） */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
