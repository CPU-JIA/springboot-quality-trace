package com.xinghui.qualitytrace.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.xinghui.qualitytrace.security.UserContext;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

/**
 * MyBatis-Plus 审计字段自动填充 —— 登记人（created_by）无感落库
 *
 * <p>工作机制：实体字段标注 {@code @TableField(fill = FieldFill.INSERT)} 后，
 * INSERT 时本处理器自动从 {@link UserContext} 取当前登录用户填充，
 * 业务代码无需（也不应）手工设置登记人——统一出口保证审计字段不可伪造。</p>
 *
 * <p>时间字段（created_at/updated_at）不在此填充：DDL 已定义
 * DEFAULT CURRENT_TIMESTAMP / ON UPDATE CURRENT_TIMESTAMP，由数据库维护，
 * 应用层与数据库两套时钟只保留一套（以库为准），避免时间源不一致。</p>
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    /** 实体中登记人字段的属性名（batch.createdBy） */
    private static final String FIELD_CREATED_BY = "createdBy";

    @Override
    public void insertFill(MetaObject metaObject) {
        // strictInsertFill：仅当实体存在该字段且值为 null 时填充（不覆盖显式赋值）
        this.strictInsertFill(metaObject, FIELD_CREATED_BY, Long.class, UserContext.currentUserId());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 更新场景无需填充：updated_at 由数据库 ON UPDATE 维护
    }
}
