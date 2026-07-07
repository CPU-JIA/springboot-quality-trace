package com.xinghui.qualitytrace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 产品质量追踪系统 · 启动类
 *
 * <p>系统定位：以【批次】为追溯基本单元，对"供应商 → 原材料批次 → 生产工单（工序、检验）
 * → 成品批次 → 客户"的完整链路建模，实现双向追溯、三级检验（IQC/IPQC/FQC）闭环、
 * 分钟级召回与质量统计分析。设计依据见 docs/02-数据库设计.md（v1.2）。</p>
 *
 * <p>技术栈（版本锁定依据 docs/01-需求分析.md §6）：
 * Spring Boot 3.5.16 + MyBatis-Plus 3.5.16 + Druid 1.2.28 + MySQL 8.4.10 + jjwt 0.13.0。</p>
 *
 * @author 星辉电器课设小组
 */
@SpringBootApplication
public class QualityTraceApplication {

    public static void main(String[] args) {
        SpringApplication.run(QualityTraceApplication.class, args);
    }
}
