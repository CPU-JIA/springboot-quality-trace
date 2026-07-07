package com.xinghui.qualitytrace.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 序列化配置 —— Long → String（防前端精度丢失）
 *
 * <p>Why：本系统主键为 BIGINT（Java Long，64 位），而 JavaScript 的 Number 只有
 * 2^53 安全整数范围。虽然本项目主键策略为数据库自增（id-type=auto，数值较小），
 * 但作为防御性契约仍将所有 Long 序列化为字符串——一旦未来切换雪花 ID（19 位），
 * 前端不会出现"id 静默取整后回传，更新影响 0 行或错行"的隐性数据事故。</p>
 *
 * <p>Why 用 Jackson2ObjectMapperBuilderCustomizer 而非直接 @Bean ObjectMapper：
 * customizer 在 Boot 默认配置（含 application.yml 的日期格式/时区）基础上【叠加】，
 * 直接声明 ObjectMapper 会整体覆盖并丢失 yml 配置。</p>
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> builder
                // 包装类型 Long 与基本类型 long 都转字符串
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance)
                // Java Time 类型默认走 ISO-8601（带 T），这里显式统一为前后端约定格式。
                .serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME_FORMATTER))
                .deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(DATE_TIME_FORMATTER));
    }
}
