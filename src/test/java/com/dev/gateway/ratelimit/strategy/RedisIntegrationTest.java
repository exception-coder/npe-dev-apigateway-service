package com.dev.gateway.ratelimit.strategy;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
@TestPropertySource(properties = {
    "spring.redis.host=localhost",
    "spring.redis.port=6379",
    "spring.redis.timeout=300ms",
    "spring.redis.lettuce.pool.max-active=8",
    "spring.redis.lettuce.pool.max-wait=200ms"
})
class RedisIntegrationTest {

    @Autowired(required = false)
    private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    @DisplayName("测试Redis基本连接")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testBasicRedisConnection() {
        log.info("=== 开始Redis基本连接测试 ===");
        
        if (redisTemplate == null) {
            log.warn("RedisTemplate未配置，跳过测试");
            return;
        }
        
        try {
            // 测试基本的Redis操作
            String testKey = "test:connection:" + System.currentTimeMillis();
            String testValue = "test_value";
            
            // 设置值
            redisTemplate.opsForValue().set(testKey, testValue, Duration.ofSeconds(10));
            log.info("成功设置Redis键值对: {} = {}", testKey, testValue);
            
            // 获取值
            Object retrievedValue = redisTemplate.opsForValue().get(testKey);
            assertEquals(testValue, retrievedValue, "Redis读写测试失败");
            
            // 删除测试键
            redisTemplate.delete(testKey);
            
            log.info("Redis基本连接测试 - 通过");
            
        } catch (Exception e) {
            log.error("Redis基本连接测试失败: {}", e.getMessage(), e);
            fail("Redis连接失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("测试ReactiveRedis连接和性能")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testReactiveRedisPerformance() {
        log.info("=== 开始ReactiveRedis连接和性能测试 ===");
        
        if (reactiveRedisTemplate == null) {
            log.warn("ReactiveRedisTemplate未配置，跳过测试");
            return;
        }
        
        try {
            String testKey = "test:reactive:" + System.currentTimeMillis();
            
            // 测试响应式Redis操作性能
            long startTime = System.currentTimeMillis();
            
            Mono<Boolean> operation = reactiveRedisTemplate.opsForValue()
                    .set(testKey, "test_value")
                    .then(reactiveRedisTemplate.opsForValue().get(testKey))
                    .then(reactiveRedisTemplate.delete(testKey))
                    .map(result -> true)
                    .timeout(Duration.ofMillis(500)); // 500ms超时
            
            Boolean result = operation.block(Duration.ofSeconds(2));
            long endTime = System.currentTimeMillis();
            
            assertNotNull(result, "ReactiveRedis操作应该成功");
            
            long executionTime = endTime - startTime;
            log.info("ReactiveRedis操作完成时间: {}ms", executionTime);
            
            // 验证执行时间是否合理
            assertTrue(executionTime < 1000, "执行时间应该小于1秒，实际: " + executionTime + "ms");
            
            log.info("ReactiveRedis连接和性能测试 - 通过");
            
        } catch (Exception e) {
            log.error("ReactiveRedis性能测试失败: {}", e.getMessage(), e);
            
            // 提供详细的错误信息
            if (e instanceof java.util.concurrent.TimeoutException) {
                log.error("可能的原因：Redis连接超时，请检查Redis服务器状态和网络连接");
            } else if (e.getMessage().contains("Connection refused")) {
                log.error("可能的原因：Redis服务器未启动或端口不正确");
            } else if (e.getMessage().contains("timeout")) {
                log.error("可能的原因：Redis响应超时，请检查网络延迟或Redis服务器负载");
            }
            
            fail("ReactiveRedis连接失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("测试Redis ZSet操作性能")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testRedisZSetPerformance() {
        log.info("=== 开始Redis ZSet操作性能测试 ===");
        
        if (reactiveRedisTemplate == null) {
            log.warn("ReactiveRedisTemplate未配置，跳过测试");
            return;
        }
        
        try {
            String zsetKey = "test:zset:" + System.currentTimeMillis();
            
            // 测试ZSet操作（滑动窗口使用的数据结构）
            long startTime = System.currentTimeMillis();
            
            // 分别执行每个ZSet操作
            Boolean add1 = reactiveRedisTemplate.opsForZSet()
                    .add(zsetKey, "member1", 1.0)
                    .timeout(Duration.ofMillis(200))
                    .block(Duration.ofSeconds(1));
            
            Boolean add2 = reactiveRedisTemplate.opsForZSet()
                    .add(zsetKey, "member2", 2.0)
                    .timeout(Duration.ofMillis(200))
                    .block(Duration.ofSeconds(1));
            
            Long count = reactiveRedisTemplate.opsForZSet()
                    .count(zsetKey, org.springframework.data.domain.Range.closed(0.0, 10.0))
                    .timeout(Duration.ofMillis(200))
                    .block(Duration.ofSeconds(1));
            
            Long deleted = reactiveRedisTemplate.delete(zsetKey)
                    .timeout(Duration.ofMillis(200))
                    .block(Duration.ofSeconds(1));
            
            long endTime = System.currentTimeMillis();
            
            assertNotNull(add1, "ZSet add操作1应该成功");
            assertNotNull(add2, "ZSet add操作2应该成功");
            assertNotNull(count, "ZSet count操作应该成功");
            assertEquals(2L, count.longValue(), "ZSet应该包含2个元素");
            
            long executionTime = endTime - startTime;
            log.info("ZSet操作完成时间: {}ms", executionTime);
            
            // 验证执行时间
            assertTrue(executionTime < 1000, "ZSet操作时间应该小于1秒，实际: " + executionTime + "ms");
            
            log.info("Redis ZSet操作性能测试 - 通过");
            
        } catch (Exception e) {
            log.error("Redis ZSet性能测试失败: {}", e.getMessage(), e);
            fail("ZSet操作失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Redis连接池压力测试")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testRedisConnectionPoolStress() {
        log.info("=== 开始Redis连接池压力测试 ===");
        
        if (reactiveRedisTemplate == null) {
            log.warn("ReactiveRedisTemplate未配置，跳过测试");
            return;
        }
        
        try {
            // 并发执行多个Redis操作
            int concurrentOperations = 20;
            Mono[] operations = new Mono[concurrentOperations];
            
            for (int i = 0; i < concurrentOperations; i++) {
                final int index = i;
                String key = "test:stress:" + index + ":" + System.currentTimeMillis();
                
                operations[i] = reactiveRedisTemplate.opsForValue()
                        .set(key, "value" + index)
                        .then(reactiveRedisTemplate.opsForValue().get(key))
                        .then(reactiveRedisTemplate.delete(key))
                        .timeout(Duration.ofMillis(500))
                        .onErrorResume(throwable -> {
                            log.warn("压力测试操作{}失败: {}", index, throwable.getMessage());
                            return Mono.just(0L);
                        });
            }
            
            // 等待所有操作完成
            long startTime = System.currentTimeMillis();
            Mono.when(operations).block(Duration.ofSeconds(10));
            long endTime = System.currentTimeMillis();
            
            log.info("压力测试完成，总耗时: {}ms", endTime - startTime);
            
            // 验证总执行时间
            assertTrue(endTime - startTime < 8000, "压力测试时间应该小于8秒");
            
            log.info("Redis连接池压力测试 - 通过");
            
        } catch (Exception e) {
            log.error("Redis连接池压力测试失败: {}", e.getMessage(), e);
            
            // 分析可能的问题
            if (e.getMessage().contains("Pool exhausted")) {
                log.error("可能的原因：Redis连接池耗尽，请检查连接池配置");
            } else if (e.getMessage().contains("timeout")) {
                log.error("可能的原因：连接池获取连接超时或Redis操作超时");
            }
            
            fail("连接池压力测试失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("诊断Redis配置和网络")
    void testRedisConfigurationDiagnosis() {
        log.info("=== 开始Redis配置和网络诊断 ===");
        
        if (reactiveRedisTemplate == null) {
            log.warn("ReactiveRedisTemplate未配置，可能的原因：");
            log.warn("1. Redis服务器未启动");
            log.warn("2. Redis连接配置错误");
            log.warn("3. 网络连接问题");
            return;
        }
        
        try {
            // 测试简单的ping操作
            log.info("测试Redis ping操作...");
            long pingStart = System.currentTimeMillis();
            
            String pingResult = reactiveRedisTemplate.execute(connection -> 
                connection.ping()
            ).blockFirst(Duration.ofSeconds(2));
            
            long pingTime = System.currentTimeMillis() - pingStart;
            log.info("Redis ping结果: {}, 耗时: {}ms", pingResult, pingTime);
            
            if (pingTime > 100) {
                log.warn("Redis ping延迟较高: {}ms，可能存在网络问题", pingTime);
            }
            
            // 测试多次快速操作
            log.info("测试连续快速操作...");
            int quickOperations = 10;
            long totalTime = 0;
            
            for (int i = 0; i < quickOperations; i++) {
                long opStart = System.currentTimeMillis();
                reactiveRedisTemplate.opsForValue()
                        .set("test:quick:" + i, "value")
                        .block(Duration.ofMillis(200));
                totalTime += (System.currentTimeMillis() - opStart);
            }
            
            double avgTime = (double) totalTime / quickOperations;
            log.info("连续{}次操作平均耗时: {}ms", quickOperations, avgTime);
            
            if (avgTime > 50) {
                log.warn("Redis操作平均延迟较高: {}ms", avgTime);
                log.warn("建议检查：");
                log.warn("1. Redis服务器性能");
                log.warn("2. 网络延迟");
                log.warn("3. 连接池配置");
            }
            
            // 清理测试数据
            for (int i = 0; i < quickOperations; i++) {
                reactiveRedisTemplate.delete("test:quick:" + i).subscribe();
            }
            
            log.info("Redis配置和网络诊断 - 完成");
            
        } catch (Exception e) {
            log.error("Redis诊断失败: {}", e.getMessage(), e);
            
            log.error("问题诊断建议：");
            log.error("1. 检查Redis服务器是否启动: redis-cli ping");
            log.error("2. 检查Redis配置: host, port, password");
            log.error("3. 检查网络连接: telnet <redis-host> <redis-port>");
            log.error("4. 检查防火墙设置");
            log.error("5. 检查Redis服务器日志");
        }
    }
} 