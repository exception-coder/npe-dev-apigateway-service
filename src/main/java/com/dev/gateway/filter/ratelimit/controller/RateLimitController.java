package com.dev.gateway.filter.ratelimit.controller;

import com.dev.gateway.filter.ratelimit.service.RateLimitService;
import com.dev.gateway.service.IpResolverService;
import lombok.extern.slf4j.Slf4j;
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

    private final RateLimitService rateLimitService;

    private final IpResolverService ipResolverService;

    public RateLimitController(RateLimitService rateLimitService, IpResolverService ipResolverService) {
        this.rateLimitService = rateLimitService;
        this.ipResolverService = ipResolverService;
    }

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
            // 验证成功：先从黑名单移除，再添加到白名单
            return rateLimitService.removeFromBlackList(clientIp)
                    .doOnNext(blacklistRemoved -> {
                        if (blacklistRemoved) {
                            log.info("验证码验证成功，IP已从黑名单移除 - IP: {}", clientIp);
                        } else {
                            log.debug("IP不在黑名单中或移除失败 - IP: {}", clientIp);
                        }
                    })
                    .then(rateLimitService.addToWhiteList(clientIp))
                    .map(whitelistAdded -> {
                        if (whitelistAdded) {
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
        return ipResolverService.getClientIp(exchange);
    }

    /**
     * 管理员接口：检查IP是否在黑名单中
     */
    @GetMapping("/admin/blacklist/check/{ip}")
    public Mono<ResponseEntity<Map<String, Object>>> checkBlackList(@PathVariable String ip) {
        log.info("管理员检查IP黑名单状态: {}", ip);

        return rateLimitService.isInBlackList(ip)
                .flatMap(isInBlackList -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("ip", ip);
                    result.put("isInBlackList", isInBlackList);

                    if (isInBlackList) {
                        // 获取黑名单详细信息
                        return rateLimitService.getBlackListInfo(ip)
                                .map(blacklistInfo -> {
                                    result.put("blacklistInfo", blacklistInfo);
                                    result.put("success", true);
                                    return ResponseEntity.ok(result);
                                });
                    } else {
                        result.put("success", true);
                        return Mono.just(ResponseEntity.ok(result));
                    }
                })
                .onErrorReturn(getErrorResponse("检查黑名单状态失败"));
    }

    /**
     * 管理员接口：手动添加IP到黑名单
     */
    @PostMapping("/admin/blacklist/{ip}")
    public Mono<ResponseEntity<Map<String, Object>>> addToBlackList(
            @PathVariable String ip,
            @RequestParam(defaultValue = "管理员手动添加") String reason,
            @RequestParam(defaultValue = "30") int durationMinutes) {
        log.info("管理员手动添加IP到黑名单: {}, 原因: {}, 有效期: {}分钟", ip, reason, durationMinutes);

        return rateLimitService.addToBlackList(ip, reason, durationMinutes)
                .map(success -> {
                    Map<String, Object> result = new HashMap<>();
                    if (success) {
                        result.put("success", true);
                        result.put("message", "IP已成功添加到黑名单");
                        result.put("ip", ip);
                        result.put("reason", reason);
                        result.put("durationMinutes", durationMinutes);
                        log.info("IP已成功添加到黑名单: {}", ip);
                    } else {
                        result.put("success", false);
                        result.put("message", "添加失败");
                        log.error("添加IP到黑名单失败: {}", ip);
                    }
                    return ResponseEntity.ok(result);
                })
                .onErrorReturn(getErrorResponse("系统异常"));
    }

    /**
     * 管理员接口：从黑名单移除IP
     */
    @DeleteMapping("/admin/blacklist/{ip}")
    public Mono<ResponseEntity<Map<String, Object>>> removeFromBlackList(@PathVariable String ip) {
        log.info("管理员从黑名单移除IP: {}", ip);

        return rateLimitService.removeFromBlackList(ip)
                .map(success -> {
                    Map<String, Object> result = new HashMap<>();
                    if (success) {
                        result.put("success", true);
                        result.put("message", "IP已从黑名单移除");
                        result.put("ip", ip);
                        log.info("IP已从黑名单移除: {}", ip);
                    } else {
                        result.put("success", false);
                        result.put("message", "IP不在黑名单中");
                        log.warn("尝试移除不存在的黑名单IP: {}", ip);
                    }
                    return ResponseEntity.ok(result);
                })
                .onErrorReturn(getErrorResponse("系统异常"));
    }

    /**
     * 获取限流状态（增强版，包含黑名单信息）
     */
    @GetMapping("/status/enhanced")
    public Mono<ResponseEntity<Map<String, Object>>> getEnhancedStatus(ServerWebExchange exchange) {
        String clientIp = getClientIp(exchange);

        return Mono.zip(
                rateLimitService.isInWhiteList(clientIp),
                rateLimitService.isInBlackList(clientIp),
                rateLimitService.isCaptchaRequired(),
                rateLimitService.getActiveIpCount()).flatMap(tuple -> {
                    boolean isWhiteListed = tuple.getT1();
                    boolean isBlackListed = tuple.getT2();
                    boolean captchaRequired = tuple.getT3();
                    long activeIpCount = tuple.getT4();

                    Map<String, Object> status = new HashMap<>();
                    status.put("clientIp", clientIp);
                    status.put("isWhiteListed", isWhiteListed);
                    status.put("isBlackListed", isBlackListed);
                    status.put("captchaRequired", captchaRequired);
                    status.put("activeIpCount", activeIpCount);

                    if (isBlackListed) {
                        // 获取黑名单详细信息
                        return rateLimitService.getBlackListInfo(clientIp)
                                .map(blacklistInfo -> {
                                    status.put("blacklistInfo", blacklistInfo);
                                    return ResponseEntity.ok(status);
                                });
                    } else {
                        return Mono.just(ResponseEntity.ok(status));
                    }
                }).onErrorReturn(getErrorResponse("获取状态失败"));
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