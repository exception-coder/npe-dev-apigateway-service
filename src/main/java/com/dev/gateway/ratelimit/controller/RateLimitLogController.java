package com.dev.gateway.ratelimit.controller;

import com.dev.gateway.ratelimit.model.RateLimitLogEntity;
import com.dev.gateway.ratelimit.service.RateLimitLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 限流日志查询控制器
 * 提供限流日志的查询和统计接口
 *
 * @author 系统
 * @version 1.0
 */
@RestController
@RequestMapping("/admin/rate-limit-logs")
@Slf4j
public class RateLimitLogController {

    @Autowired
    private RateLimitLogService rateLimitLogService;

    /**
     * 根据IP地址查询限流日志
     * 
     * @param clientIp  客户端IP
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 限流日志列表
     */
    @GetMapping("/by-ip")
    public Flux<RateLimitLogEntity> getLogsByIp(
            @RequestParam String clientIp,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        log.info("查询IP限流日志 - IP: {}, 时间范围: {} - {}", clientIp, startTime, endTime);
        return rateLimitLogService.findLogsByIp(clientIp, startTime, endTime)
                .doOnError(error -> log.error("查询IP限流日志失败: {}", error.getMessage(), error));
    }

    /**
     * 根据限流类型查询日志
     * 
     * @param rateLimitType 限流类型
     * @param startTime     开始时间
     * @param endTime       结束时间
     * @return 限流日志列表
     */
    @GetMapping("/by-type")
    public Flux<RateLimitLogEntity> getLogsByType(
            @RequestParam String rateLimitType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        log.info("查询限流类型日志 - 类型: {}, 时间范围: {} - {}", rateLimitType, startTime, endTime);
        return rateLimitLogService.findLogsByType(rateLimitType, startTime, endTime)
                .doOnError(error -> log.error("查询限流类型日志失败: {}", error.getMessage(), error));
    }

    /**
     * 统计指定时间范围内的限流次数
     * 
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 限流次数
     */
    @GetMapping("/count")
    public Mono<ResponseEntity<Long>> countRateLimitLogs(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        log.info("统计限流日志数量 - 时间范围: {} - {}", startTime, endTime);
        return rateLimitLogService.countRateLimitLogs(startTime, endTime)
                .map(ResponseEntity::ok)
                .doOnError(error -> log.error("统计限流日志失败: {}", error.getMessage(), error))
                .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    /**
     * 查询DDoS攻击日志
     * 
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return DDoS攻击日志列表
     */
    @GetMapping("/ddos")
    public Flux<RateLimitLogEntity> getDdosLogs(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        log.info("查询DDoS攻击日志 - 时间范围: {} - {}", startTime, endTime);
        return rateLimitLogService.findDdosLogs(startTime, endTime)
                .doOnError(error -> log.error("查询DDoS攻击日志失败: {}", error.getMessage(), error));
    }

    /**
     * 手动清理过期日志
     * 
     * @param beforeDays 清理多少天前的日志
     * @return 操作结果
     */
    @PostMapping("/cleanup")
    public Mono<ResponseEntity<String>> cleanupLogs(@RequestParam(defaultValue = "30") int beforeDays) {
        try {
            LocalDateTime cleanupTime = LocalDateTime.now().minusDays(beforeDays);
            log.info("手动清理限流日志 - 清理时间点: {}", cleanupTime);

            return rateLimitLogService.cleanExpiredLogs(cleanupTime)
                    .map(count -> ResponseEntity.ok("日志清理完成，清理了 " + count + " 条记录，清理时间点: " + cleanupTime))
                    .doOnError(error -> log.error("手动清理日志失败: {}", error.getMessage(), error))
                    .onErrorReturn(ResponseEntity.internalServerError().body("清理失败"));
        } catch (Exception e) {
            log.error("手动清理日志异常: {}", e.getMessage(), e);
            return Mono.just(ResponseEntity.internalServerError().body("清理异常: " + e.getMessage()));
        }
    }
}