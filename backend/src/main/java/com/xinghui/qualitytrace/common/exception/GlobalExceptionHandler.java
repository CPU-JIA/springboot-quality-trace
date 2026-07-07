package com.xinghui.qualitytrace.common.exception;

import com.xinghui.qualitytrace.common.result.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器 —— 所有 Controller 抛出的异常在此统一转换为 {@link Result} 结构
 *
 * <p>分层处理策略（错误处理必须分级，不能一把 500 糊弄）：
 * <ol>
 *   <li>{@link BusinessException}：业务规则拒绝——预期内异常，用其自带 code 与中文
 *       消息返回，日志记 warn（不是错误，是业务防线在工作）；</li>
 *   <li>{@link MethodArgumentNotValidException}：@Validated 参数校验失败——拼接
 *       各字段错误为一条可读消息返回 400；</li>
 *   <li>MVC 层异常：未知接口、请求方法不支持、路径/枚举参数类型错误——转成明确中文
 *       404/400，避免用户误以为系统故障；</li>
 *   <li>{@link DuplicateKeyException}：唯一约束冲突——数据库层防线兜住的重复提交
 *       （如单号并发撞号、重复授权），返回 400 提示"重复提交或编号冲突"；</li>
 *   <li>其余 Exception：未预期异常——日志记 error 全栈（便于排障），对外仅返回
 *       兜底话术，绝不泄漏内部细节（SQL/堆栈/路径）。</li>
 * </ol></p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：预期内的规则拒绝，warn 级日志 + 原样透出中文提示 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务拒绝: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** 参数校验失败：把各字段的校验消息拼为一条，如 "数量必须为正数; 名称不能为空" */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return Result.fail(message);
    }

    /** 请求参数约束失败：主要覆盖 @RequestParam / @PathVariable 上的校验注解 */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("请求参数约束失败: {}", message);
        return Result.fail(message);
    }

    /** 请求参数缺失：如调用 /api/process-routes 时未传 materialId */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("请求参数缺失: {}", e.getMessage());
        return Result.fail("缺少必要请求参数：" + e.getParameterName());
    }

    /** 路径变量、查询参数类型错误：如 id 传成空串、枚举传未知值 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("请求参数类型错误: {}", e.getMessage());
        return Result.fail("请求参数[" + e.getName() + "]格式不正确，请检查后重试");
    }

    /** 请求体不可读（JSON 语法错误/类型不匹配）：提示调用方检查请求格式 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleUnreadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.fail("请求体格式错误，请检查 JSON 语法与字段类型");
    }

    /** 请求体类型转换失败：补齐 JSON 枚举/日期等反序列化错误的中文提示 */
    @ExceptionHandler(HttpMessageConversionException.class)
    public Result<Void> handleMessageConversion(HttpMessageConversionException e) {
        log.warn("请求体字段转换失败: {}", e.getMessage());
        return Result.fail("请求体字段格式错误，请检查枚举、日期与数字格式");
    }

    /** 未知 API 路径：Spring Boot 3 会把未匹配路径包装为 NoResourceFoundException */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResource(NoResourceFoundException e) {
        log.warn("接口不存在: {}", e.getResourcePath());
        return Result.fail(404, "接口不存在，请检查请求地址");
    }

    /** HTTP 方法不支持：如把 POST 接口用 GET 调用 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {}", e.getMessage());
        return Result.fail("当前接口不支持 " + e.getMethod() + " 请求");
    }

    /** 唯一约束冲突：数据库层防线（UNIQUE）拦下的重复数据 */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一约束冲突: {}", e.getMessage());
        return Result.fail("数据重复提交或编号冲突，请刷新后重试");
    }

    /**
     * 引用完整性冲突：外键 ON DELETE RESTRICT 拦下的删除操作。
     * 设计语义（docs/02 §5.8）：质量追溯数据有下游引用即不可删——这不是故障，
     * 是数据库防线在正确工作，转译为业务指引提示用户改用"停用"。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Void> handleIntegrity(DataIntegrityViolationException e) {
        log.warn("引用完整性冲突: {}", e.getMessage());
        String detail = e.getMostSpecificCause() == null ? "" : e.getMostSpecificCause().getMessage();
        if (detail.contains("Cannot delete or update a parent row")) {
            return Result.fail("该数据已被业务数据引用，不可删除；如需下线请使用停用功能");
        }
        return Result.fail("数据不符合业务约束，请检查必填项、状态取值与关联关系");
    }

    /** 兜底：未预期异常——全栈落日志，对外只给通用话术（不泄漏内部实现） */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnexpected(Exception e) {
        log.error("未预期异常", e);
        return Result.fail(500, "系统繁忙，请稍后重试");
    }
}
