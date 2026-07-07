package com.xinghui.qualitytrace.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI（springdoc 2.8.17）配置 —— 接口文档元信息 + Bearer 认证方案
 *
 * <p>访问入口：http://localhost:8081/swagger-ui/index.html；
 * 原始文档：/v3/api-docs（A7 阶段将其保存为 openapi.json 作为报告附录与前端契约冻结）。</p>
 *
 * <p>Bearer 方案的作用：swagger 页面右上角 Authorize 按钮——粘贴登录接口返回的
 * 令牌后，后续"Try it out"自动携带 Authorization 头，可直接调试受保护接口。</p>
 */
@Configuration
public class OpenApiConfig {

    private static final String SCHEME_NAME = "BearerAuth";

    @Bean
    public OpenAPI qualityTraceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("产品质量追踪系统 API")
                        .description("数据库课程设计 —— 批次级双向追溯 / IQC·IPQC·FQC 三级检验 / 召回管理 / 质量统计。"
                                + "演示账号（密码均为 123456）：admin（管理员）、zhangsan（仓库）、"
                                + "lisi（生产）、wangwu（质检）、zhaoliu（质量主管）。")
                        .version("v1.0.0"))
                // 声明全局 Bearer 认证方案，使 swagger-ui 出现 Authorize 入口
                .components(new Components().addSecuritySchemes(SCHEME_NAME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME_NAME));
    }
}
