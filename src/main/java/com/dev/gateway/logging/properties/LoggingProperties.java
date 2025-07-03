package com.dev.gateway.logging.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 日志配置属性
 * 支持通过配置文件动态调整日志参数
 */
@Data
@Component
@ConfigurationProperties(prefix = "gateway.logging")
public class LoggingProperties {

    /**
     * 是否启用访问日志
     */
    private boolean accessLogEnabled = true;

    /**
     * 是否启用请求体日志
     */
    private boolean requestBodyEnabled = false;

    /**
     * 是否启用响应体日志
     */
    private boolean responseBodyEnabled = false;

    /**
     * 是否启用详细日志
     */
    private boolean verboseLogging = false;

    /**
     * 最大请求体记录长度
     */
    private int maxRequestBodyLength = 1000;

    /**
     * 最大响应体记录长度
     */
    private int maxResponseBodyLength = 1000;

    /**
     * 需要跳过日志记录的路径
     */
    private String[] skipPaths = {
            "/actuator/**",
            "/health/**"
    };

    /**
     * 敏感信息过滤关键词
     */
    private String[] sensitiveFields = {
            "password",
            "token",
            "secret",
            "key",
            "authorization"
    };

    /**
     * 是否记录请求头
     */
    private boolean logHeaders = false;

    /**
     * 是否记录响应头
     */
    private boolean logResponseHeaders = false;

    /**
     * 日志级别
     */
    private String logLevel = "INFO";
} 