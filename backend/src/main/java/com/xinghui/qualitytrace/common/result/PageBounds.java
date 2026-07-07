package com.xinghui.qualitytrace.common.result;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinghui.qualitytrace.common.exception.BusinessException;

/**
 * 分页入参边界 —— 所有列表查询进入数据库前统一校验。
 *
 * <p>前端普通表格默认 10 条，下拉全量装载使用 100 条分批拉取；因此服务端
 * 将单页上限固定为 100，既覆盖真实页面需求，也避免误传超大 size 造成慢查询。</p>
 */
public final class PageBounds {

    private static final long MAX_SIZE = 100;

    private PageBounds() {
    }

    /** 校验并创建 MyBatis-Plus 分页对象。 */
    public static <T> Page<T> page(long current, long size) {
        if (current < 1) {
            throw new BusinessException("页码必须从1开始");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BusinessException("每页条数必须在1到100之间");
        }
        return new Page<>(current, size);
    }
}
