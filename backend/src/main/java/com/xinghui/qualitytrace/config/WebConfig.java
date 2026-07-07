package com.xinghui.qualitytrace.config;

import com.xinghui.qualitytrace.security.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置 —— JWT 拦截器挂载 + CORS 跨域
 *
 * <p>拦截范围设计：只拦 /api/**（全部业务接口都在此前缀下）：
 * <ul>
 *   <li>白名单 /api/auth/login（登录本身不能要求已登录）；</li>
 *   <li>/swagger-ui/**、/v3/api-docs/** 不在 /api 下，天然不拦（接口文档可匿名浏览，
 *       调试受保护接口时在 swagger 的 Authorize 里粘贴令牌）；</li>
 *   <li>/druid/** 由 Druid 自带 Servlet 提供，不走 MVC 拦截器，自有独立登录。</li>
 * </ul></p>
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login");
    }

    /**
     * CORS：前端 Vite 开发服务器（localhost:5174）跨源调用后端 8081。
     * 课设开发环境放开全部来源；令牌走 Authorization 头而非 Cookie，
     * 无需 allowCredentials。生产部署应收紧为具体域名。
     */
    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
