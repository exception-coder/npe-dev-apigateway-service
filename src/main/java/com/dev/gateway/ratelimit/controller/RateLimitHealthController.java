package com.dev.gateway.ratelimit.controller;

import com.dev.gateway.ratelimit.service.RedisHealthCheckService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 限流服务健康检查控制器
 * 提供Redis连接状态和限流服务健康状况查询
 */
@Slf4j
@RestController
@RequestMapping("/api/rate-limit/health")
public class RateLimitHealthController {

    @Autowired
    private RedisHealthCheckService redisHealthCheckService;

    /**
     * 获取Redis连接健康状态
     */
    @GetMapping("/redis")
    public Mono<ResponseEntity<Map<String, Object>>> getRedisHealth() {
        return redisHealthCheckService.checkHealthNow()
                .map(isHealthy -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("healthy", isHealthy);
                    response.put("consecutiveFailures", redisHealthCheckService.getConsecutiveFailures());
                    response.put("lastSuccessTime", redisHealthCheckService.getLastSuccessTime());
                    response.put("lastFailureTime", redisHealthCheckService.getLastFailureTime());
                    response.put("timestamp", LocalDateTime.now());
                    
                    if (isHealthy) {
                        response.put("status", "UP");
                        response.put("message", "Redis连接正常");
                        return ResponseEntity.ok(response);
                    } else {
                        response.put("status", "DOWN");
                        response.put("message", "Redis连接异常");
                        return ResponseEntity.status(503).body(response);
                    }
                })
                .onErrorResume(throwable -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("healthy", false);
                    response.put("status", "ERROR");
                    response.put("message", "健康检查失败: " + throwable.getMessage());
                    response.put("timestamp", LocalDateTime.now());
                    
                    log.error("Redis健康检查失败", throwable);
                    return Mono.just(ResponseEntity.status(500).body(response));
                });
    }

    /**
     * 获取限流服务整体健康状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getOverallHealth() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean redisHealthy = redisHealthCheckService.isRedisHealthy();
            int consecutiveFailures = redisHealthCheckService.getConsecutiveFailures();
            
            response.put("rateLimitService", "UP");
            response.put("redis", Map.of(
                    "healthy", redisHealthy,
                    "status", redisHealthy ? "UP" : "DOWN",
                    "consecutiveFailures", consecutiveFailures,
                    "lastSuccessTime", redisHealthCheckService.getLastSuccessTime(),
                    "lastFailureTime", redisHealthCheckService.getLastFailureTime()
            ));
            
            // 整体状态判断
            if (redisHealthy) {
                response.put("status", "UP");
                response.put("message", "限流服务运行正常");
                return ResponseEntity.ok(response);
            } else {
                response.put("status", "DEGRADED");
                response.put("message", "Redis连接异常，但限流服务已降级运行");
                return ResponseEntity.status(200).body(response); // 返回200但标记为降级状态
            }
            
        } catch (Exception e) {
            response.put("status", "ERROR");
            response.put("message", "健康检查异常: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            
            log.error("限流服务健康检查异常", e);
            return ResponseEntity.status(500).body(response);
        }
    }
} 