package com.xinghui.qualitytrace.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Getter;

import java.util.List;

/**
 * 分页响应体 —— 列表类接口的统一分页外壳
 *
 * <p>前后端契约：请求以 {@code current}（页码，从 1 起）与 {@code size}（页大小）
 * 传参；响应固定为 {@code {records, total, current, size}}。</p>
 *
 * <p>Why 不直接返回 MyBatis-Plus 的 {@link IPage}：IPage 是 ORM 框架类型，直接外露
 * 会把框架序列化细节（orders/optimizeCountSql 等内部字段）泄漏到接口契约中；
 * 以本类转换后契约稳定，未来更换 ORM 不影响前端。</p>
 *
 * @param <T> 行记录类型
 */
@Getter
public class PageResult<T> {

    /** 当前页数据行 */
    private final List<T> records;

    /** 满足条件的总记录数 */
    private final long total;

    /** 当前页码（从 1 起） */
    private final long current;

    /** 页大小 */
    private final long size;

    private PageResult(List<T> records, long total, long current, long size) {
        this.records = records;
        this.total = total;
        this.current = current;
        this.size = size;
    }

    /** 由 MyBatis-Plus 分页结果转换（最常用入口） */
    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 手工组装（用于视图查询等非 IPage 场景） */
    public static <T> PageResult<T> of(List<T> records, long total, long current, long size) {
        return new PageResult<>(records, total, current, size);
    }
}
