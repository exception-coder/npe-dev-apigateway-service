package com.dev.gateway.logging.configuration;

import org.springframework.context.annotation.Configuration;

/**
 * 日志模块配置类
 * 提供日志相关的Bean配置
 */
@Configuration
public class LoggingConfiguration {
    // 目前不需要额外的Bean配置
    // 所有日志组件都通过@Component、@Service、@RestController等注解自动注册
} 