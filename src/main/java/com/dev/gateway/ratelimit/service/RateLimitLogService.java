package com.dev.gateway.ratelimit.service;

import com.dev.gateway.ratelimit.model.RateLimitLogEntity;
import com.dev.gateway.ratelimit.repository.RateLimitLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 限流日志服务类
 * 提供限流日志的记录、查询和管理功能
 *
 * @author 系统
 * @version 1.0
 */
@Service
@Slf4j
public class RateLimitLogService {

    private final RateLimitLogRepository rateLimitLogRepository;

    private final ObjectMapper objectMapper;

    public RateLimitLogService(RateLimitLogRepository rateLimitLogRepository, ObjectMapper objectMapper) {
        this.rateLimitLogRepository = rateLimitLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 异步记录限流日志
     * 
     * @param exchange      请求上下文
     * @param clientIp      客户端IP
     * @param rateLimitType 限流类型
     * @param limitReason   限流原因
     * @param currentCount  当前计数
     * @param threshold     阈值
     * @param windowSize    窗口大小
     */
    @Async
    public void recordRateLimitLog(ServerWebExchange exchange, String clientIp, String rateLimitType,
            String limitReason, Integer currentCount, Integer threshold, Integer windowSize) {
        try {
            ServerHttpRequest request = exchange.getRequest();

            RateLimitLogEntity logEntity = RateLimitLogEntity.builder()
                    .clientIp(clientIp)
                    .requestPath(request.getURI().getPath())
                    .httpMethod(request.getMethod() != null ? request.getMethod().name() : "UNKNOWN")
                    .rateLimitType(rateLimitType)
                    .limitReason(limitReason)
                    .userAgent(request.getHeaders().getFirst("User-Agent"))
                    .requestHeaders(convertHeadersToJson(request.getHeaders().toSingleValueMap()))
                    .currentRequestCount(currentCount)
                    .limitThreshold(threshold)
                    .windowSizeSeconds(windowSize)
                    .triggerTime(LocalDateTime.now())
                    .responseStatus(429) // Too Many Requests
                    .inWhiteList((Boolean) exchange.getAttributes().get("whiteList5minutesFlatMap"))
                    .isDdosAttack(isDdosRelated(rateLimitType))
                    .createTime(LocalDateTime.now())
                    .requestId(request.getId())
                    .build();

            // 使用响应式保存，但不等待结果
            rateLimitLogRepository.save(logEntity)
                    .doOnSuccess(saved -> {
                        if (log.isDebugEnabled()) {
                            log.debug("限流日志记录成功 - IP: {}, 类型: {}, 原因: {}", clientIp, rateLimitType, limitReason);
                        }
                    })
                    .doOnError(error -> log.error("记录限流日志失败 - IP: {}, 类型: {}, 错误: {}", clientIp, rateLimitType,
                            error.getMessage()))
                    .subscribe(); // 异步执行，不阻塞主流程

        } catch (Exception e) {
            log.error("记录限流日志异常 - IP: {}, 类型: {}, 错误: {}", clientIp, rateLimitType, e.getMessage(), e);
        }
    }

    /**
     * 异步记录DDoS攻击日志
     * 
     * @param exchange      请求上下文
     * @param clientIp      客户端IP
     * @param rateLimitType 限流类型
     * @param limitReason   限流原因
     * @param activeIpCount 活跃IP数量
     * @param threshold     阈值
     */
    @Async
    public void recordDdosLog(ServerWebExchange exchange, String clientIp, String rateLimitType,
            String limitReason, Integer activeIpCount, Integer threshold) {
        try {
            ServerHttpRequest request = exchange.getRequest();

            RateLimitLogEntity logEntity = RateLimitLogEntity.builder()
                    .clientIp(clientIp)
                    .requestPath(request.getURI().getPath())
                    .httpMethod(request.getMethod() != null ? request.getMethod().name() : "UNKNOWN")
                    .rateLimitType(rateLimitType)
                    .limitReason(limitReason)
                    .userAgent(request.getHeaders().getFirst("User-Agent"))
                    .requestHeaders(convertHeadersToJson(request.getHeaders().toSingleValueMap()))
                    .activeIpCount(activeIpCount)
                    .limitThreshold(threshold)
                    .triggerTime(LocalDateTime.now())
                    .responseStatus(302) // Redirect to captcha
                    .inWhiteList((Boolean) exchange.getAttributes().get("whiteList5minutesFlatMap"))
                    .isDdosAttack(true)
                    .createTime(LocalDateTime.now())
                    .requestId(request.getId())
                    .build();

            // 使用响应式保存，但不等待结果
            rateLimitLogRepository.save(logEntity)
                    .doOnSuccess(saved -> log.warn("DDoS攻击日志记录成功 - IP: {}, 活跃IP数: {}, 阈值: {}", clientIp, activeIpCount,
                            threshold))
                    .doOnError(error -> log.error("记录DDoS攻击日志失败 - IP: {}, 错误: {}", clientIp, error.getMessage()))
                    .subscribe(); // 异步执行，不阻塞主流程

        } catch (Exception e) {
            log.error("记录DDoS攻击日志异常 - IP: {}, 错误: {}", clientIp, e.getMessage(), e);
        }
    }

    /**
     * 异步记录验证码触发日志
     * 
     * @param exchange    请求上下文
     * @param clientIp    客户端IP
     * @param limitReason 限流原因
     * @param ipCount     当前IP数量
     */
    @Async
    public void recordCaptchaLog(ServerWebExchange exchange, String clientIp, String limitReason, Integer ipCount) {
        try {
            ServerHttpRequest request = exchange.getRequest();

            RateLimitLogEntity logEntity = RateLimitLogEntity.builder()
                    .clientIp(clientIp)
                    .requestPath(request.getURI().getPath())
                    .httpMethod(request.getMethod() != null ? request.getMethod().name() : "UNKNOWN")
                    .rateLimitType("CAPTCHA_REQUIRED")
                    .limitReason(limitReason)
                    .userAgent(request.getHeaders().getFirst("User-Agent"))
                    .requestHeaders(convertHeadersToJson(request.getHeaders().toSingleValueMap()))
                    .currentRequestCount(ipCount)
                    .triggerTime(LocalDateTime.now())
                    .responseStatus(429) // Too Many Requests
                    .inWhiteList((Boolean) exchange.getAttributes().get("whiteList5minutesFlatMap"))
                    .isDdosAttack(false)
                    .createTime(LocalDateTime.now())
                    .requestId(request.getId())
                    .build();

            // 使用响应式保存，但不等待结果
            rateLimitLogRepository.save(logEntity)
                    .doOnSuccess(saved -> log.info("验证码触发日志记录成功 - IP: {}, 原因: {}", clientIp, limitReason))
                    .doOnError(error -> log.error("记录验证码触发日志失败 - IP: {}, 错误: {}", clientIp, error.getMessage()))
                    .subscribe(); // 异步执行，不阻塞主流程

        } catch (Exception e) {
            log.error("记录验证码触发日志异常 - IP: {}, 错误: {}", clientIp, e.getMessage(), e);
        }
    }

    /**
     * 根据IP地址查询限流日志
     * 
     * @param clientIp  客户端IP
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 限流日志列表
     */
    public Flux<RateLimitLogEntity> findLogsByIp(String clientIp, LocalDateTime startTime, LocalDateTime endTime) {
        return rateLimitLogRepository.findByClientIpAndTriggerTimeBetween(clientIp, startTime, endTime);
    }

    /**
     * 根据限流类型查询日志
     * 
     * @param rateLimitType 限流类型
     * @param startTime     开始时间
     * @param endTime       结束时间
     * @return 限流日志列表
     */
    public Flux<RateLimitLogEntity> findLogsByType(String rateLimitType, LocalDateTime startTime,
            LocalDateTime endTime) {
        return rateLimitLogRepository.findByRateLimitTypeAndTriggerTimeBetween(rateLimitType, startTime, endTime);
    }

    /**
     * 统计指定时间范围内的限流次数
     * 
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 限流次数
     */
    public Mono<Long> countRateLimitLogs(LocalDateTime startTime, LocalDateTime endTime) {
        return rateLimitLogRepository.countByTriggerTimeBetween(startTime, endTime);
    }

    /**
     * 查询DDoS攻击日志
     * 
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return DDoS攻击日志列表
     */
    public Flux<RateLimitLogEntity> findDdosLogs(LocalDateTime startTime, LocalDateTime endTime) {
        return rateLimitLogRepository.findByIsDdosAttackTrueAndTriggerTimeBetween(startTime, endTime);
    }

    /**
     * 清理过期日志
     * 
     * @param beforeTime 清理时间点
     * @return 清理的记录数量
     */
    public Mono<Long> cleanExpiredLogs(LocalDateTime beforeTime) {
        return rateLimitLogRepository.deleteByTriggerTimeBefore(beforeTime)
                .doOnSuccess(count -> log.info("清理过期限流日志完成，清理了 {} 条记录，清理时间点: {}", count, beforeTime))
                .doOnError(error -> log.error("清理过期限流日志失败: {}", error.getMessage(), error));
    }

    /**
     * 将HTTP头转换为JSON字符串
     */
    private String convertHeadersToJson(Map<String, String> headers) {
        try {
            // 过滤敏感信息
            Map<String, String> filteredHeaders = headers.entrySet().stream()
                    .filter(entry -> !isSensitiveHeader(entry.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            return objectMapper.writeValueAsString(filteredHeaders);
        } catch (JsonProcessingException e) {
            log.warn("转换HTTP头为JSON失败: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 判断是否为敏感HTTP头
     */
    private boolean isSensitiveHeader(String headerName) {
        String lowerName = headerName.toLowerCase();
        return lowerName.contains("authorization") ||
                lowerName.contains("cookie") ||
                lowerName.contains("token") ||
                lowerName.contains("password");
    }

    /**
     * 判断是否为DDoS相关的限流类型
     */
    private boolean isDdosRelated(String rateLimitType) {
        return "DDOS_PROTECTION".equals(rateLimitType) ||
                "DDOS_THRESHOLD".equals(rateLimitType);
    }
}