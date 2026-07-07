package com.xinghui.qualitytrace.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码学配置 —— BCrypt 密码编码器
 *
 * <p>来源说明：仅引入 spring-security-crypto 模块获得 BCrypt 实现，
 * 未引入 Spring Security 完整框架（无过滤器链），认证由自实现 JWT 拦截器负责。</p>
 *
 * <p>BCrypt 特性（答辩要点）：自带随机盐（同一明文每次哈希结果不同）、
 * 单向不可逆（数据库泄露也无法还原明文）、成本因子可调（默认 10，2^10 轮），
 * 校验用 {@code matches(明文, 摘要)} 而非重新哈希比对。</p>
 */
@Configuration
public class CryptoConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
