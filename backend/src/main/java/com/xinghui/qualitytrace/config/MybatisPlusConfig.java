package com.xinghui.qualitytrace.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置 —— 分页插件注册
 *
 * <p>关键依赖说明：MyBatis-Plus 3.5.9 起分页能力解耦到 mybatis-plus-jsqlparser 模块，
 * pom 中已引入（版本与主 starter 严格一致 3.5.16）。</p>
 *
 * <p>坑位警示：分页拦截器【未注册】的症状是静默失败——Page 查询返回全量数据且
 * total=0，不报任何错。A1 验收清单中的"分页断言"即为验证此配置生效。</p>
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 指定 MySQL 方言：分页语句生成 LIMIT，count 语句自动优化
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 后端兜底限制单页数量，防止绕过前端分页控件提交超大 size 造成慢查询或内存压力。
        pagination.setMaxLimit(100L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
