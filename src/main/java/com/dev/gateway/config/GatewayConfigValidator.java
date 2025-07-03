package com.dev.gateway.config;

import com.dev.gateway.properties.GatewayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 网关配置验证器
 * 在应用启动时验证配置参数的合法性
 * 
 * @author 系统
 * @version 1.0
 */
@Slf4j
@Component
public class GatewayConfigValidator implements CommandLineRunner {

    private final GatewayProperties gatewayProperties;

    public GatewayConfigValidator(GatewayProperties gatewayProperties) {
        this.gatewayProperties = gatewayProperties;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("开始验证网关配置参数...");
        
        validateRateLimitConfig();
        validateCaptchaConfig();
        validateSecurityConfig();
        
        log.info("网关配置参数验证完成");
        printCurrentConfig();
    }

    /**
     * 验证限流配置
     */
    private void validateRateLimitConfig() {
        GatewayProperties.RateLimitConfig rateLimit = gatewayProperties.getRateLimit();
        
        if (rateLimit == null) {
            throw new IllegalArgumentException("限流配置不能为空");
        }
        
        if (!StringUtils.hasText(rateLimit.getDefaultClientIp())) {
            throw new IllegalArgumentException("默认客户端IP不能为空");
        }
        
        // 验证IP格式
        if (!isValidIpAddress(rateLimit.getDefaultClientIp())) {
            log.warn("默认客户端IP格式可能不正确: {}", rateLimit.getDefaultClientIp());
        }
        
        log.info("限流配置验证通过 - 启用状态: {}, 默认IP: {}",
                rateLimit.isEnabled(), rateLimit.getDefaultClientIp());
    }

    /**
     * 验证验证码配置
     */
    private void validateCaptchaConfig() {
        GatewayProperties.CaptchaConfig captcha = gatewayProperties.getCaptcha();
        
        if (captcha == null) {
            throw new IllegalArgumentException("验证码配置不能为空");
        }
        
        if (!StringUtils.hasText(captcha.getPagePath())) {
            throw new IllegalArgumentException("验证码页面路径不能为空");
        }
        
        if (captcha.getTriggerIpThreshold() <= 0) {
            throw new IllegalArgumentException("触发验证码的IP阈值必须大于0");
        }
        
        if (captcha.getReleaseIpThreshold() <= 0) {
            throw new IllegalArgumentException("解除验证码的IP阈值必须大于0");
        }
        
        if (captcha.getTriggerIpThreshold() <= captcha.getReleaseIpThreshold()) {
            log.warn("触发阈值({})应该大于解除阈值({})", 
                    captcha.getTriggerIpThreshold(), captcha.getReleaseIpThreshold());
        }
        
        if (captcha.getIpStatisticsWindowSeconds() <= 0) {
            throw new IllegalArgumentException("IP统计窗口时间必须大于0秒");
        }
        
        if (captcha.getWhitelistValidityMinutes() <= 0) {
            throw new IllegalArgumentException("白名单有效期必须大于0分钟");
        }
        
        log.info("验证码配置验证通过 - 页面路径: {}, 触发阈值: {}, 解除阈值: {}, 统计窗口: {}秒", 
                captcha.getPagePath(), captcha.getTriggerIpThreshold(), 
                captcha.getReleaseIpThreshold(), captcha.getIpStatisticsWindowSeconds());
    }

    /**
     * 验证安全配置
     */
    private void validateSecurityConfig() {
        GatewayProperties.SecurityConfig security = gatewayProperties.getSecurity();
        
        if (security == null) {
            throw new IllegalArgumentException("安全配置不能为空");
        }
        
        if (!StringUtils.hasText(security.getContentSecurityPolicy())) {
            log.warn("内容安全策略(CSP)为空，可能存在安全风险");
        }
        
        if (!StringUtils.hasText(security.getReferrerPolicy())) {
            log.warn("Referrer策略为空，可能存在信息泄露风险");
        }
        
        if (!StringUtils.hasText(security.getFrameOptions())) {
            log.warn("X-Frame-Options为空，可能存在点击劫持风险");
        }
        
        log.info("安全配置验证通过 - 安全头启用: {}, Cookie安全: {}, CSP: {}", 
                security.isEnableSecurityHeaders(), security.isEnableSecureCookies(), 
                security.getContentSecurityPolicy());
    }

    /**
     * 验证IP地址格式
     */
    private boolean isValidIpAddress(String ip) {
        if (!StringUtils.hasText(ip)) {
            return false;
        }
        
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        
        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 打印当前配置信息
     */
    private void printCurrentConfig() {
        log.info("=== 当前网关配置概览 ===");
        
        GatewayProperties.RateLimitConfig rateLimit = gatewayProperties.getRateLimit();
        log.info("限流配置: 启用={}, 默认IP={}",
                rateLimit.isEnabled(), rateLimit.getDefaultClientIp());
        
        GatewayProperties.CaptchaConfig captcha = gatewayProperties.getCaptcha();
        log.info("验证码配置: 页面={}, 触发阈值={}, 解除阈值={}, 窗口={}秒, 白名单={}分钟", 
                captcha.getPagePath(), captcha.getTriggerIpThreshold(), captcha.getReleaseIpThreshold(),
                captcha.getIpStatisticsWindowSeconds(), captcha.getWhitelistValidityMinutes());
        
        GatewayProperties.SecurityConfig security = gatewayProperties.getSecurity();
        log.info("安全配置: 安全头={}, 安全Cookie={}, Frame选项={}", 
                security.isEnableSecurityHeaders(), security.isEnableSecureCookies(), security.getFrameOptions());
        
        log.info("=========================");
    }
} 