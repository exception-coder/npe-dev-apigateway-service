package com.dev.gateway.ratelimit.controller;

import com.dev.gateway.ratelimit.service.RateLimitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 限流控制器
 * 提供验证码验证和白名单管理功能
 */
@RestController
@RequestMapping("/api/rate-limit")
@Slf4j
public class RateLimitController {

    @Autowired
    private RateLimitService rateLimitService;
    
    @Autowired
    @Qualifier("rateLimitIpResolver")
    private XForwardedRemoteAddressResolver xForwardedRemoteAddressResolver;

    /**
     * 验证码验证接口
     * 验证成功后将IP添加到白名单
     */
    @PostMapping("/verify-captcha")
    public Mono<ResponseEntity<Map<String, Object>>> verifyCaptcha(
            @RequestParam String captcha,
            ServerWebExchange exchange) {
        
        String clientIp = getClientIp(exchange);
        log.info("验证码验证请求 - IP: {}, 验证码: {}", clientIp, captcha);
        
        Map<String, Object> result = new HashMap<>();
        
        // 这里可以根据实际需求实现验证码验证逻辑
        // 简单示例：假设验证码为"1234"时验证通过
        if ("1234".equals(captcha)) {
            return rateLimitService.addToWhiteList(clientIp)
                    .map(success -> {
                        if (success) {
                            log.info("验证码验证成功，IP已添加到白名单 - IP: {}", clientIp);
                            result.put("success", true);
                            result.put("message", "验证成功，已添加到白名单");
                            result.put("redirectUrl", "/");
                        } else {
                            log.error("添加IP到白名单失败 - IP: {}", clientIp);
                            result.put("success", false);
                            result.put("message", "系统错误，请重试");
                        }
                        return ResponseEntity.ok(result);
                    })
                    .onErrorReturn(getErrorResponse("系统异常，请重试"));
        } else {
            log.warn("验证码验证失败 - IP: {}, 验证码: {}", clientIp, captcha);
            result.put("success", false);
            result.put("message", "验证码错误");
            return Mono.just(ResponseEntity.ok(result));
        }
    }

    /**
     * 获取当前限流状态信息
     */
    @GetMapping("/status")
    public Mono<ResponseEntity<Map<String, Object>>> getStatus(ServerWebExchange exchange) {
        String clientIp = getClientIp(exchange);
        
        // 检查IP是否在白名单中
        return rateLimitService.isInWhiteList(clientIp)
                .flatMap(isWhiteListed -> {
                    Map<String, Object> status = new HashMap<>();
                    status.put("clientIp", clientIp);
                    status.put("isWhiteListed", isWhiteListed);
                    
                    // 检查是否需要验证码
                    return rateLimitService.isCaptchaRequired()
                            .flatMap(captchaRequired -> {
                                status.put("captchaRequired", captchaRequired);
                                
                                // 获取活跃IP数量
                                return rateLimitService.getActiveIpCount()
                                        .map(activeIpCount -> {
                                            status.put("activeIpCount", activeIpCount);
                                            return ResponseEntity.ok(status);
                                        });
                            });
                })
                .onErrorReturn(getErrorResponse("获取状态失败"));
    }

    /**
     * 管理员接口：手动添加IP到白名单
     */
    @PostMapping("/admin/whitelist/{ip}")
    public Mono<ResponseEntity<Map<String, Object>>> addToWhiteList(@PathVariable String ip) {
        log.info("管理员手动添加IP到白名单: {}", ip);
        
        return rateLimitService.addToWhiteList(ip)
                .map(success -> {
                    Map<String, Object> result = new HashMap<>();
                    if (success) {
                        result.put("success", true);
                        result.put("message", "IP已成功添加到白名单");
                        log.info("IP已成功添加到白名单: {}", ip);
                    } else {
                        result.put("success", false);
                        result.put("message", "添加失败");
                        log.error("添加IP到白名单失败: {}", ip);
                    }
                    return ResponseEntity.ok(result);
                })
                .onErrorReturn(getErrorResponse("系统异常"));
    }

    /**
     * 管理员接口：从白名单移除IP
     */
    @DeleteMapping("/admin/whitelist/{ip}")
    public Mono<ResponseEntity<Map<String, Object>>> removeFromWhiteList(@PathVariable String ip) {
        log.info("管理员从白名单移除IP: {}", ip);
        
        return rateLimitService.removeFromWhiteList(ip)
                .map(success -> {
                    Map<String, Object> result = new HashMap<>();
                    if (success) {
                        result.put("success", true);
                        result.put("message", "IP已从白名单移除");
                        log.info("IP已从白名单移除: {}", ip);
                    } else {
                        result.put("success", false);
                        result.put("message", "IP不在白名单中");
                        log.warn("尝试移除不存在的白名单IP: {}", ip);
                    }
                    return ResponseEntity.ok(result);
                })
                .onErrorReturn(getErrorResponse("系统异常"));
    }

    /**
     * 管理员接口：重置验证码机制
     */
    @PostMapping("/admin/reset-captcha")
    public Mono<ResponseEntity<Map<String, Object>>> resetCaptcha() {
        log.info("管理员重置验证码机制");
        
        return rateLimitService.disableCaptchaRequired()
                .map(success -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("message", "验证码机制已重置");
                    log.info("验证码机制已重置");
                    return ResponseEntity.ok(result);
                })
                .onErrorReturn(getErrorResponse("重置失败"));
    }

    /**
     * 管理员接口：获取限流统计信息
     */
    @GetMapping("/admin/stats")
    public Mono<ResponseEntity<Map<String, Object>>> getStats() {
        log.info("管理员获取限流统计信息");
        
        return rateLimitService.getActiveIpCount()
                .flatMap(activeIpCount -> {
                    return rateLimitService.isCaptchaRequired()
                            .map(captchaRequired -> {
                                Map<String, Object> stats = new HashMap<>();
                                stats.put("activeIpCount", activeIpCount);
                                stats.put("captchaRequired", captchaRequired);
                                stats.put("timestamp", System.currentTimeMillis());
                                
                                return ResponseEntity.ok(stats);
                            });
                })
                .onErrorReturn(getErrorResponse("获取统计信息失败"));
    }

    /**
     * 管理员接口：清理过期数据
     */
    @PostMapping("/admin/cleanup")
    public Mono<ResponseEntity<Map<String, Object>>> cleanupExpiredData() {
        log.info("管理员清理过期数据");
        
        return rateLimitService.cleanupExpiredCounters()
                .map(cleanedCount -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("message", "清理完成");
                    result.put("cleanedCount", cleanedCount);
                    log.info("清理过期数据完成，清理数量: {}", cleanedCount);
                    return ResponseEntity.ok(result);
                })
                .onErrorReturn(getErrorResponse("清理失败"));
    }

    /**
     * 获取客户端IP地址
     */
    private String getClientIp(ServerWebExchange exchange) {
        String mockIp = exchange.getRequest().getHeaders().getFirst("Mock-IP");
        if (mockIp != null && !mockIp.isEmpty()) {
            return mockIp;
        }
        
        return xForwardedRemoteAddressResolver.resolve(exchange).getAddress().getHostAddress();
    }

    /**
     * 生成错误响应
     */
    private ResponseEntity<Map<String, Object>> getErrorResponse(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", message);
        return ResponseEntity.ok(result);
    }
} 