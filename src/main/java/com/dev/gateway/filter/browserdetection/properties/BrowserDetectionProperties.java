package com.dev.gateway.filter.browserdetection.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 浏览器检测配置属性
 * 用于配置浏览器检测的各种参数和规则
 */
@Data
@Component
@ConfigurationProperties(prefix = "gateway.browser-detection")
public class BrowserDetectionProperties {

    /**
     * 是否启用浏览器检测
     */
    private boolean enabled = true;

    /**
     * 检测严格程度 (STRICT, MODERATE, LOOSE)
     */
    private StrictnessLevel strictness = StrictnessLevel.MODERATE;

    /**
     * 是否启用详细日志
     */
    private boolean verboseLogging = false;

    /**
     * 需要跳过检测的路径
     */
    private String[] skipPaths = {
            "/actuator/**",
            "/health/**",
            "/api/browser-detection/**",
            "/dev/static/**"
    };

    /**
     * 已知的爬虫User-Agent关键词（黑名单）
     */
    private String[] botUserAgents = {
            "bot", "crawl", "spider", "scrape", "fetch", "curl", "wget", "python", "java", "go-http-client",
            "okhttp", "apache-httpclient", "requests", "urllib", "mechanize", "scrapy", "phantom", "headless",
            "automation", "selenium", "webdriver", "puppeteer", "playwright", "test"
    };

    /**
     * 真实浏览器User-Agent关键词（白名单）
     */
    private String[] realBrowserUserAgents = {
            "Mozilla", "Chrome", "Safari", "Firefox", "Edge", "Opera", "Brave"
    };

    /**
     * 真实浏览器必需的请求头
     */
    private String[] requiredBrowserHeaders = {
            "Accept",
            "Accept-Language", 
            "Accept-Encoding",
            "Connection"
    };

    /**
     * 可疑的请求头（通常爬虫会包含）
     */
    private String[] suspiciousHeaders = {
            "X-Requested-With",
            "X-Forwarded-Proto",
            "X-Real-IP"
    };

    /**
     * User-Agent最小长度
     */
    private int minUserAgentLength = 20;

    /**
     * User-Agent最大长度
     */
    private int maxUserAgentLength = 1000;

    /**
     * 是否检查JavaScript支持标识
     */
    private boolean checkJavaScriptSupport = true;

    /**
     * 拒绝请求时的响应消息
     */
    private String rejectionMessage = "Access denied: Non-browser request detected";

    /**
     * 拒绝请求时的状态码
     */
    private int rejectionStatusCode = 403;

    /**
     * 检测严格程度枚举
     */
    public enum StrictnessLevel {
        /**
         * 严格模式：严格检查所有浏览器特征
         */
        STRICT,
        
        /**
         * 中等模式：平衡检测准确性和兼容性
         */
        MODERATE,
        
        /**
         * 宽松模式：只检测明显的爬虫工具
         */
        LOOSE
    }

    /**
     * 获取跳过路径列表
     */
    public List<String> getSkipPathsList() {
        return Arrays.asList(skipPaths);
    }

    /**
     * 获取爬虫User-Agent列表
     */
    public List<String> getBotUserAgentsList() {
        return Arrays.asList(botUserAgents);
    }

    /**
     * 获取真实浏览器User-Agent列表
     */
    public List<String> getRealBrowserUserAgentsList() {
        return Arrays.asList(realBrowserUserAgents);
    }

    /**
     * 获取必需请求头列表
     */
    public List<String> getRequiredBrowserHeadersList() {
        return Arrays.asList(requiredBrowserHeaders);
    }

    /**
     * 获取可疑请求头列表
     */
    public List<String> getSuspiciousHeadersList() {
        return Arrays.asList(suspiciousHeaders);
    }
} 