package com.dev.gateway.ratelimit.diagnosis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Redis连接诊断工具
 * 用于诊断Redis超时问题的根本原因
 */
@Slf4j
@Component
public class RedisConnectionDiagnostics {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final AtomicInteger testCounter = new AtomicInteger(0);

    public RedisConnectionDiagnostics(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 执行完整的Redis连接诊断
     */
    public void performDiagnosis() {
        log.info("🔍 开始Redis连接诊断...");
        
        // 1. 基础连接测试
        testBasicConnection();
        
        // 2. 延迟测试
        testLatency();
        
        // 3. 并发测试
        testConcurrency();
        
        // 4. 调度器测试
        testSchedulers();
        
        log.info("🔍 Redis连接诊断完成");
    }

    /**
     * 测试基础连接
     */
    private void testBasicConnection() {
        log.info("📡 测试基础Redis连接...");
        
        try {
            long startTime = System.currentTimeMillis();
            
            String result = redisTemplate.execute(connection -> connection.ping())
                    .timeout(Duration.ofSeconds(2))
                    .blockFirst(Duration.ofSeconds(3));
            
            long endTime = System.currentTimeMillis();
            
            if (result != null) {
                log.info("✅ Redis Ping成功，耗时: {}ms", endTime - startTime);
            } else {
                log.error("❌ Redis Ping失败：无响应");
            }
            
        } catch (Exception e) {
            log.error("❌ Redis基础连接测试失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 测试Redis操作延迟
     */
    private void testLatency() {
        log.info("⏱️ 测试Redis操作延迟...");
        
        for (int i = 0; i < 5; i++) {
            try {
                String testKey = "diagnosis:latency:" + testCounter.incrementAndGet();
                long startTime = System.currentTimeMillis();
                
                Boolean result = redisTemplate.opsForValue()
                        .set(testKey, "test_value")
                        .timeout(Duration.ofMillis(500))
                        .block(Duration.ofSeconds(2));
                
                long endTime = System.currentTimeMillis();
                long latency = endTime - startTime;
                
                if (result != null && result) {
                    if (latency > 200) {
                        log.warn("⚠️ Redis操作延迟较高: {}ms (第{}次)", latency, i + 1);
                    } else {
                        log.info("✅ Redis操作正常: {}ms (第{}次)", latency, i + 1);
                    }
                } else {
                    log.error("❌ Redis操作失败 (第{}次)", i + 1);
                }
                
                // 清理测试数据
                redisTemplate.delete(testKey).subscribe();
                
                Thread.sleep(100); // 间隔100ms
                
            } catch (Exception e) {
                log.error("❌ Redis延迟测试失败 (第{}次): {}", i + 1, e.getMessage());
            }
        }
    }

    /**
     * 测试并发Redis操作
     */
    private void testConcurrency() {
        log.info("🔄 测试并发Redis操作...");
        
        try {
            int concurrentCount = 10;
            Mono<Boolean>[] operations = new Mono[concurrentCount];
            
            long startTime = System.currentTimeMillis();
            
            for (int i = 0; i < concurrentCount; i++) {
                final int index = i;
                String testKey = "diagnosis:concurrent:" + index + ":" + testCounter.incrementAndGet();
                
                operations[i] = redisTemplate.opsForValue()
                        .set(testKey, "concurrent_value_" + index)
                        .timeout(Duration.ofMillis(500))
                        .doFinally(signalType -> {
                            // 清理测试数据
                            redisTemplate.delete(testKey).subscribe();
                        })
                        .onErrorResume(throwable -> {
                            log.warn("并发测试操作{}失败: {}", index, throwable.getMessage());
                            return Mono.just(false);
                        });
            }
            
            // 等待所有操作完成
            Mono.when(operations)
                    .timeout(Duration.ofSeconds(5))
                    .block(Duration.ofSeconds(6));
            
            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            
            log.info("✅ 并发测试完成，总耗时: {}ms，平均每个操作: {}ms", 
                    totalTime, totalTime / concurrentCount);
            
            if (totalTime > 2000) {
                log.warn("⚠️ 并发操作耗时较长，可能存在连接池或调度器问题");
            }
            
        } catch (Exception e) {
            log.error("❌ 并发测试失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 测试不同调度器的性能
     */
    private void testSchedulers() {
        log.info("🧵 测试不同调度器性能...");
        
        // 测试默认调度器
        testWithScheduler("默认调度器", null);
        
        // 测试弹性调度器
        testWithScheduler("弹性调度器", Schedulers.boundedElastic());
        
        // 测试并行调度器
        testWithScheduler("并行调度器", Schedulers.parallel());
    }

    private void testWithScheduler(String schedulerName, reactor.core.scheduler.Scheduler scheduler) {
        try {
            String testKey = "diagnosis:scheduler:" + testCounter.incrementAndGet();
            long startTime = System.currentTimeMillis();
            
            Mono<Boolean> operation = redisTemplate.opsForValue()
                    .set(testKey, "scheduler_test")
                    .timeout(Duration.ofMillis(500));
            
            if (scheduler != null) {
                operation = operation.subscribeOn(scheduler);
            }
            
            Boolean result = operation.block(Duration.ofSeconds(2));
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            if (result != null && result) {
                log.info("✅ {} 测试成功，耗时: {}ms", schedulerName, duration);
            } else {
                log.error("❌ {} 测试失败", schedulerName);
            }
            
            // 清理测试数据
            redisTemplate.delete(testKey).subscribe();
            
        } catch (Exception e) {
            log.error("❌ {} 测试异常: {}", schedulerName, e.getMessage());
        }
    }

    /**
     * 快速诊断单个操作
     */
    public void quickDiagnosis() {
        log.info("🚀 执行快速Redis诊断...");
        
        String testKey = "diagnosis:quick:" + System.currentTimeMillis();
        long startTime = System.currentTimeMillis();
        
        try {
            Boolean result = redisTemplate.opsForValue()
                    .set(testKey, "quick_test")
                    .subscribeOn(Schedulers.boundedElastic())
                    .timeout(Duration.ofMillis(300))
                    .doOnSuccess(success -> {
                        long duration = System.currentTimeMillis() - startTime;
                        if (duration > 100) {
                            log.warn("⚠️ 快速诊断耗时较长: {}ms", duration);
                        } else {
                            log.info("✅ 快速诊断正常: {}ms", duration);
                        }
                    })
                    .doFinally(signalType -> {
                        redisTemplate.delete(testKey).subscribe();
                    })
                    .block(Duration.ofSeconds(1));
            
            if (result == null || !result) {
                log.error("❌ 快速诊断失败");
            }
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("❌ 快速诊断异常 (耗时: {}ms): {}", duration, e.getMessage());
            
            if (e instanceof java.util.concurrent.TimeoutException) {
                log.error("💡 建议检查：");
                log.error("   1. Redis服务器状态和性能");
                log.error("   2. 网络连接延迟");
                log.error("   3. 连接池配置是否合理");
                log.error("   4. 应用程序线程池状态");
            }
        }
    }
} 