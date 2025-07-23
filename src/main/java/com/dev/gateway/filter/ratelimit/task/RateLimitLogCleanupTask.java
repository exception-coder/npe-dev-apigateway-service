package com.dev.gateway.filter.ratelimit.task;

import com.dev.gateway.filter.ratelimit.service.RateLimitLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 限流日志清理定时任务
 * 定期清理过期的限流日志数据
 *
 * @author 系统
 * @version 1.0
 */
@Component
@Slf4j
public class RateLimitLogCleanupTask {

    @Autowired
    private RateLimitLogService rateLimitLogService;

    /**
     * 每天凌晨2点执行日志清理任务
     * 清理30天前的限流日志
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupExpiredLogs() {
        try {
            LocalDateTime cleanupTime = LocalDateTime.now().minusDays(30);
            log.info("开始清理过期限流日志，清理时间点: {}", cleanupTime);

            // 使用响应式方法，阻塞等待结果
            rateLimitLogService.cleanExpiredLogs(cleanupTime)
                    .doOnSuccess(count -> log.info("限流日志清理任务完成，清理了 {} 条记录", count))
                    .doOnError(error -> log.error("限流日志清理任务执行失败: {}", error.getMessage(), error))
                    .block(); // 阻塞等待完成

        } catch (Exception e) {
            log.error("限流日志清理任务异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 每周日凌晨3点执行深度清理
     * 清理90天前的所有数据
     */
    @Scheduled(cron = "0 0 3 ? * SUN")
    public void deepCleanupLogs() {
        try {
            LocalDateTime deepCleanupTime = LocalDateTime.now().minusDays(90);
            log.info("开始深度清理限流日志，清理时间点: {}", deepCleanupTime);

            // 使用响应式方法，阻塞等待结果
            rateLimitLogService.cleanExpiredLogs(deepCleanupTime)
                    .doOnSuccess(count -> log.info("限流日志深度清理任务完成，清理了 {} 条记录", count))
                    .doOnError(error -> log.error("限流日志深度清理任务执行失败: {}", error.getMessage(), error))
                    .block(); // 阻塞等待完成

        } catch (Exception e) {
            log.error("限流日志深度清理任务异常: {}", e.getMessage(), e);
        }
    }
}