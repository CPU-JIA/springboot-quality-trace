package com.xinghui.qualitytrace.common.result;

import lombok.Getter;

/**
 * 统一响应体 —— 全部接口的返回外壳（前后端契约的核心约定之一）
 *
 * <p>结构固定为 {@code {code, message, data}}：
 * <ul>
 *   <li>code = 200：业务成功，data 携带载荷；</li>
 *   <li>code = 400：业务失败（参数非法/状态机拒绝/余量不足等），message 为可直接
 *       展示给用户的中文提示，前端统一 toast；</li>
 *   <li>code = 401：未认证（无令牌/令牌过期），前端跳转登录页；</li>
 *   <li>code = 403：已认证但角色无权限；</li>
 *   <li>code = 500：未预期的服务端异常（兜底），message 不暴露内部细节。</li>
 * </ul></p>
 *
 * <p>Why 泛型静态工厂而非构造器：调用点 {@code Result.ok(data)} / {@code Result.fail(msg)}
 * 语义自明，且避免调用方误组装出 code 与 data 不一致的响应。</p>
 *
 * @param <T> 业务载荷类型
 */
@Getter
public class Result<T> {

    /** 业务状态码（非 HTTP 状态码；HTTP 层统一返回 200，由本码表达业务结果） */
    private final int code;

    /** 提示消息：成功时为 "success"，失败时为面向用户的中文说明 */
    private final String message;

    /** 业务数据载荷：失败时为 null */
    private final T data;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 成功——携带数据 */
    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "success", data);
    }

    /** 成功——无数据载荷（写操作完成类接口） */
    public static Result<Void> ok() {
        return new Result<>(200, "success", null);
    }

    /** 业务失败——默认 400 码 */
    public static <T> Result<T> fail(String message) {
        return new Result<>(400, message, null);
    }

    /** 业务失败——自定义码（401 未认证 / 403 无权限 / 500 兜底） */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
