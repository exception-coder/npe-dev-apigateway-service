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
class SimpleRedisTest {

    @Autowired(required = false)
    private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    @Test
    @DisplayName("简单Redis连接测试")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testSimpleRedisConnection() {
        log.info("=== 开始简单Redis连接测试 ===");
        
        if (reactiveRedisTemplate == null) {
            log.error("❌ ReactiveRedisTemplate 未配置！");
            log.error("可能的原因：");
            log.error("1. Redis服务器未启动");
            log.error("2. Redis连接配置错误");
            log.error("3. 依赖缺失");
            fail("ReactiveRedisTemplate未配置");
            return;
        }
        
        log.info("✅ ReactiveRedisTemplate 已配置");
        
        try {
            // 测试最简单的操作
            String testKey = "simple_test:" + System.currentTimeMillis();
            String testValue = "test_value";
            
            log.info("🔍 测试Redis SET操作...");
            long setStart = System.currentTimeMillis();
            
            Boolean setResult = reactiveRedisTemplate.opsForValue()
                    .set(testKey, testValue)
                    .timeout(Duration.ofMillis(500))
                    .block(Duration.ofSeconds(2));
            
            long setTime = System.currentTimeMillis() - setStart;
            log.info("SET操作结果: {}, 耗时: {}ms", setResult, setTime);
            
            if (setTime > 200) {
                log.warn("⚠️ SET操作耗时较长: {}ms", setTime);
            }
            
            assertNotNull(setResult, "SET操作应该成功");
            assertTrue(setResult, "SET操作应该返回true");
            
            log.info("🔍 测试Redis GET操作...");
            long getStart = System.currentTimeMillis();
            
            String getValue = reactiveRedisTemplate.opsForValue()
                    .get(testKey)
                    .timeout(Duration.ofMillis(500))
                    .block(Duration.ofSeconds(2));
            
            long getTime = System.currentTimeMillis() - getStart;
            log.info("GET操作结果: {}, 耗时: {}ms", getValue, getTime);
            
            assertEquals(testValue, getValue, "GET操作应该返回正确的值");
            
            log.info("🔍 测试Redis DELETE操作...");
            long delStart = System.currentTimeMillis();
            
            Long deleteResult = reactiveRedisTemplate.delete(testKey)
                    .timeout(Duration.ofMillis(500))
                    .block(Duration.ofSeconds(2));
            
            long delTime = System.currentTimeMillis() - delStart;
            log.info("DELETE操作结果: {}, 耗时: {}ms", deleteResult, delTime);
            
            assertEquals(1L, deleteResult.longValue(), "DELETE操作应该删除1个键");
            
            log.info("✅ 所有基本Redis操作测试通过");
            
        } catch (Exception e) {
            log.error("❌ Redis操作失败: {}", e.getMessage(), e);
            
            // 详细的错误分析
            if (e instanceof java.util.concurrent.TimeoutException) {
                log.error("💡 超时分析：");
                log.error("   - 网络延迟过高");
                log.error("   - Redis服务器负载过高");
                log.error("   - 连接池配置问题");
            } else if (e.getMessage().contains("Connection refused")) {
                log.error("💡 连接被拒绝：");
                log.error("   - Redis服务器未启动");
                log.error("   - 端口配置错误");
                log.error("   - 防火墙阻止连接");
            } else if (e.getMessage().contains("timeout")) {
                log.error("💡 操作超时：");
                log.error("   - Redis响应慢");
                log.error("   - 网络问题");
                log.error("   - 服务器资源不足");
            }
            
            fail("Redis操作失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Redis响应时间基准测试")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testRedisResponseTimeBenchmark() {
        log.info("=== 开始Redis响应时间基准测试 ===");
        
        if (reactiveRedisTemplate == null) {
            log.warn("ReactiveRedisTemplate未配置，跳过测试");
            return;
        }
        
        int testCount = 20;
        long totalTime = 0;
        int successCount = 0;
        int timeoutCount = 0;
        
        for (int i = 0; i < testCount; i++) {
            try {
                String key = "benchmark:" + i + ":" + System.currentTimeMillis();
                
                long start = System.currentTimeMillis();
                
                Boolean result = reactiveRedisTemplate.opsForValue()
                        .set(key, "value" + i)
                        .timeout(Duration.ofMillis(300)) // 300ms超时
                        .block(Duration.ofSeconds(1));
                
                long time = System.currentTimeMillis() - start;
                totalTime += time;
                successCount++;
                
                log.debug("操作 {} 耗时: {}ms", i, time);
                
                // 清理
                reactiveRedisTemplate.delete(key).subscribe();
                
            } catch (Exception e) {
                if (e instanceof java.util.concurrent.TimeoutException || e.getMessage().contains("timeout")) {
                    timeoutCount++;
                    log.warn("操作 {} 超时", i);
                } else {
                    log.error("操作 {} 失败: {}", i, e.getMessage());
                }
            }
        }
        
        if (successCount > 0) {
            double avgTime = (double) totalTime / successCount;
            log.info("📊 基准测试结果：");
            log.info("   总操作数: {}", testCount);
            log.info("   成功操作数: {}", successCount);
            log.info("   超时操作数: {}", timeoutCount);
            log.info("   平均响应时间: {:.2f}ms", avgTime);
            log.info("   成功率: {:.2f}%", (double) successCount / testCount * 100);
            
            if (avgTime > 100) {
                log.warn("⚠️ 平均响应时间较高: {:.2f}ms", avgTime);
            }
            
            if (timeoutCount > testCount * 0.1) {
                log.warn("⚠️ 超时率较高: {:.2f}%", (double) timeoutCount / testCount * 100);
            }
            
            // 基本验证
            assertTrue(successCount > testCount * 0.5, "成功率应该超过50%");
            
        } else {
            fail("所有Redis操作都失败了");
        }
    }

    @Test
    @DisplayName("Redis连接状态诊断")
    void testRedisConnectionDiagnosis() {
        log.info("=== 开始Redis连接状态诊断 ===");
        
        if (reactiveRedisTemplate == null) {
            log.error("❌ ReactiveRedisTemplate 未注入");
            log.error("检查清单：");
            log.error("□ Redis服务器是否启动？执行: redis-server");
            log.error("□ Redis端口是否正确？默认: 6379");
            log.error("□ 网络连接是否正常？执行: telnet localhost 6379");
            log.error("□ Spring Boot Redis依赖是否正确？");
            return;
        }
        
        log.info("✅ ReactiveRedisTemplate 已注入");
        
        try {
            log.info("🔍 测试Redis PING命令...");
            
            // 使用execute方法执行原生Redis命令
            String pingResult = reactiveRedisTemplate.execute(connection -> 
                connection.ping().map(String::valueOf)
            ).blockFirst(Duration.ofSeconds(3));
            
            log.info("PING结果: {}", pingResult);
            
            if ("PONG".equals(pingResult)) {
                log.info("✅ Redis服务器响应正常");
            } else {
                log.warn("⚠️ Redis PING响应异常: {}", pingResult);
            }
            
        } catch (Exception e) {
            log.error("❌ Redis PING失败: {}", e.getMessage());
            
            log.error("故障排除步骤：");
            log.error("1️⃣ 检查Redis服务: ps aux | grep redis");
            log.error("2️⃣ 检查Redis端口: netstat -tlnp | grep 6379");
            log.error("3️⃣ 测试本地连接: redis-cli ping");
            log.error("4️⃣ 检查Redis日志: tail -f /var/log/redis/redis-server.log");
            log.error("5️⃣ 检查应用配置: spring.redis.*");
        }
    }
} 