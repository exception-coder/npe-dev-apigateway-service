package com.dev.gateway.properties;

import com.alibaba.nacos.shaded.com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import javax.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Data
@ConfigurationProperties(prefix = "gateway.sys")
public class GatewayProperties {

    /**
     * 系统及对应部署主机内网ip关系
     */
    private Map<String, List<String>> sysIpsMap;

    /**
     * 应用部署主机内网ip对应系统关系
     */
    private Map<String, String> ipSysMap = Maps.newHashMap();

    private DataSize maxRequestSize;

    private DataSize maxFileSize;

    /**
     * 限流配置
     */
    private RateLimitConfig rateLimit = new RateLimitConfig();

    /**
     * 验证码配置
     */
    private CaptchaConfig captcha = new CaptchaConfig();

    /**
     * 安全配置
     */
    private SecurityConfig security = new SecurityConfig();

    public void setSysIpsMap(Map<String, List<String>> sysIpsMap) {
        this.sysIpsMap = sysIpsMap;
        sysIpsMap.forEach((k, vs) -> {
            vs.forEach(v -> {
                ipSysMap.put(v, k);
            });
        });
    }

    /**
     * 限流配置类
     */
    @Data
    public static class RateLimitConfig {

        /**
         * 是否启用限流机制
         */
        private boolean enabled = true;

        /**
         * 默认客户端IP（用于Mock或测试环境）
         */
        private String defaultClientIp = "127.0.0.1";

        /**
         * 滑动窗口配置
         */
        private SlidingWindowConfig slidingWindow = new SlidingWindowConfig();

    }

    /**
     * 滑动窗口配置类
     */
    @Data
    public static class SlidingWindowConfig {

        /**
         * 存储类型：LOCAL_MEMORY（本地内存）或 REDIS（Redis存储）
         */
        private String storageType = "LOCAL_MEMORY";

        /**
         * 默认时间窗口大小（秒）
         */
        private int defaultWindowSize = 1;

        /**
         * 默认时间窗口内允许的最大请求数
         */
        private int defaultMaxRequests = 1;

        /**
         * 路径级别的限流规则
         * key: 路径模式（支持Spring通配符，如 /api/user/**, /static/*)
         * value: 路径限流配置
         */
        private List<PathRateLimitRule> pathRules = Lists.newArrayList();

        public Map<String, PathRateLimitRule> getPathRulesMap() {
            Map<String, PathRateLimitRule> ruleMap = pathRules.stream()
                    .collect(Collectors.toMap(PathRateLimitRule::getPath, Function.identity()));
            return ruleMap;
        }

    }

    /**
     * 路径限流规则
     */
    @Data
    public static class PathRateLimitRule {

        private String path;

        /**
         * 时间窗口大小（秒）
         */
        private int windowSize = 60;

        /**
         * 时间窗口内允许的最大请求数
         */
        private int maxRequests = 10;

        /**
         * 是否启用该规则
         */
        private boolean enabled = true;

        /**
         * 规则描述
         */
        private String description;

    }

    /**
     * 验证码配置类
     */
    @Data
    public static class CaptchaConfig {

        /**
         * 验证码页面路径
         */
        private String pagePath = "/dev/static/captcha.html";

        /**
         * 触发验证码机制的IP数量阈值
         */
        private long triggerIpThreshold = 50L;

        /**
         * 解除验证码机制的IP数量阈值
         */
        private long releaseIpThreshold = 10L;

        /**
         * IP统计窗口时间（秒）
         */
        private int ipStatisticsWindowSeconds = 10;

        /**
         * 白名单有效期（分钟）
         */
        private int whitelistValidityMinutes = 5;

        /**
         * 是否启用严格验证码模式（启用后所有请求都需要验证码验证）
         */
        private boolean strictMode = false;

        /**
         * 是否允许API请求在验证码激活时继续访问
         */
        private boolean allowApiWhenCaptchaActive = true;
    }

    /**
     * 安全配置类
     */
    @Data
    public static class SecurityConfig {

        /**
         * 是否启用HTTP安全头
         */
        private boolean enableSecurityHeaders = true;

        /**
         * 是否启用Cookie安全设置
         */
        private boolean enableSecureCookies = true;

        /**
         * 内容安全策略
         */
        private String contentSecurityPolicy = "default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; frame-src 'self';";

        /**
         * Referrer策略
         */
        private String referrerPolicy = "no-referrer";

        /**
         * X-Frame-Options值
         */
        private String frameOptions = "SAMEORIGIN";
    }
}
