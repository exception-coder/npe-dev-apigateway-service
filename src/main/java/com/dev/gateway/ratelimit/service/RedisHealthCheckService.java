package com.dev.gateway.ratelimit.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Redis健康检查服务
 * 定期检查Redis连接状态，提供连接健康度监控
 */
@Service
@Slf4j
public class RedisHealthCheckService {

    @Autowired
    @Qualifier("rateLimitRedisTemplate")
    private ReactiveRedisTemplate<String, String> redisTemplate;

    private final AtomicBoolean isRedisHealthy = new AtomicBoolean(true);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile LocalDateTime lastSuccessTime = LocalDateTime.now();
    private volatile LocalDateTime lastFailureTime;

    private static final String HEALTH_CHECK_KEY = "rate_limit:health_check";
    private static final int MAX_CONSECUTIVE_FAILURES = 5;
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(10);

    /**
     * 定期健康检查 - 每30秒执行一次
     */
    @Scheduled(fixedRate = 30000)
    public void performHealthCheck() {
        redisTemplate.opsForValue()
                .set(HEALTH_CHECK_KEY, String.valueOf(System.currentTimeMillis()), Duration.ofMinutes(1))
                .timeout(HEALTH_CHECK_TIMEOUT)
                .subscribe(
                        result -> onHealthCheckSuccess(),
                        throwable -> onHealthCheckFailure(throwable)
                );
    }

    /**
     * 健康检查成功处理
     */
    private void onHealthCheckSuccess() {
        boolean wasUnhealthy = !isRedisHealthy.get();
        consecutiveFailures.set(0);
        isRedisHealthy.set(true);
        lastSuccessTime = LocalDateTime.now();

        if (wasUnhealthy) {
            log.info("Redis连接已恢复正常 - 时间: {}", lastSuccessTime);
        } else {
            log.debug("Redis健康检查正常 - 时间: {}", lastSuccessTime);
        }
    }

    /**
     * 健康检查失败处理
     */
    private void onHealthCheckFailure(Throwable throwable) {
        int failures = consecutiveFailures.incrementAndGet();
        lastFailureTime = LocalDateTime.now();

        log.warn("Redis健康检查失败 - 连续失败次数: {}, 错误: {}", failures, throwable.getMessage());

        if (failures >= MAX_CONSECUTIVE_FAILURES) {
            boolean wasHealthy = isRedisHealthy.getAndSet(false);
            if (wasHealthy) {
                log.error("Redis连接状态异常！连续失败{}次，标记为不健康状态 - 时间: {}", 
                        failures, lastFailureTime);
            }
        }
    }

    /**
     * 检查Redis是否健康
     */
    public boolean isRedisHealthy() {
        return isRedisHealthy.get();
    }

    /**
     * 获取连续失败次数
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    /**
     * 获取最后成功时间
     */
    public LocalDateTime getLastSuccessTime() {
        return lastSuccessTime;
    }

    /**
     * 获取最后失败时间
     */
    public LocalDateTime getLastFailureTime() {
        return lastFailureTime;
    }

    /**
     * 立即执行健康检查
     */
    public Mono<Boolean> checkHealthNow() {
        return redisTemplate.opsForValue()
                .set(HEALTH_CHECK_KEY, String.valueOf(System.currentTimeMillis()), Duration.ofMinutes(1))
                .timeout(HEALTH_CHECK_TIMEOUT)
                .map(result -> {
                    onHealthCheckSuccess();
                    return true;
                })
                .onErrorResume(throwable -> {
                    onHealthCheckFailure(throwable);
                    return Mono.just(false);
                });
    }
} 