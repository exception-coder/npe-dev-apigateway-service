package com.dev.gateway.ratelimit.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 限流配置属性
 * 支持通过配置文件动态调整限流参数
 */
@Data
@Component
@ConfigurationProperties(prefix = "gateway.rate-limit")
public class RateLimitProperties {

    /**
     * 是否启用限流
     */
    private boolean enabled = true;

    /**
     * 每分钟最大请求数
     */
    private int maxRequestsPerMinute = 60;

    /**
     * 每秒最大请求数
     */
    private int maxRequestsPerSecond = 10;

    /**
     * 触发DDoS防护的IP数量阈值
     */
    private int ddosThresholdIpCount = 50;

    /**
     * 解除DDoS防护的IP数量阈值
     */
    private int ddosReleaseIpCount = 10;

    /**
     * 白名单有效期（分钟）
     */
    private int whiteListDurationMinutes = 5;

    /**
     * 黑名单有效期（分钟）
     */
    private int blackListDurationMinutes = 30;

    /**
     * 是否启用黑名单功能
     */
    private boolean blackListEnabled = true;

    /**
     * 验证码机制有效期（分钟）
     */
    private int captchaDurationMinutes = 5;

    /**
     * IP访问跟踪时间窗口（秒）
     */
    private int ipTrackDurationSeconds = 10;

    /**
     * Redis键前缀
     */
    private String redisKeyPrefix = "rate_limit";

    /**
     * 需要跳过限流检查的路径
     */
    private String[] skipPaths = {
            "/static/captcha.html",
            "/api/rate-limit/**",
            "/actuator/**"
    };

    /**
     * 是否启用详细日志
     */
    private boolean verboseLogging = false;

    /**
     * 验证码页面路径
     * 支持以下格式：
     * 1. 完整URL：http://example.com/captcha.html 或 https://example.com/captcha.html
     * 2. 相对路径：/static/captcha.html（将基于baseUrl或当前请求构建完整URL）
     */
    private String captchaPagePath = "/static/captcha.html";

    /**
     * 网关基础URL（可选）
     * 当captchaPagePath为相对路径时，会与此baseUrl拼接
     * 例如：https://api.example.com
     */
    private String baseUrl;
}