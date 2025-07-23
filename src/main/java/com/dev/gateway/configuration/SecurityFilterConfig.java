package com.dev.gateway.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 安全过滤器配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "security.filter")
public class SecurityFilterConfig {

    /**
     * 是否启用XSS检查
     */
    private boolean enableXssCheck = true;

    /**
     * 是否启用详细日志
     */
    private boolean enableDetailedLogging = false;

    /**
     * 跳过XSS清理的路径列表
     */
    private String[] skipPaths = { "/actuator/**", "/health", "/favicon.ico" };

    /**
     * 跳过XSS清理的用户代理列表
     */
    private String[] skipUserAgents = { "HealthCheck", "Prometheus" };
}