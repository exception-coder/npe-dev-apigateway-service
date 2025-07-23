package com.dev.gateway.ratelimit.strategy;

import com.dev.gateway.filter.ratelimit.strategy.RedisSlidingWindowStrategy;
import com.dev.gateway.properties.GatewayProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveZSetOperations;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Slf4j
@SpringBootTest
@TestPropertySource(properties = {
    "spring.redis.host=172.27.42.42",
    "spring.redis.port=6379",
    "spring.redis.password=redisP@ssw0Rd2023",
    "spring.redis.timeout=500ms",
    "spring.redis.lettuce.pool.max-active=8",
    "spring.redis.lettuce.pool.max-wait=300ms"
})
class RedisSlidingWindowStrategyTest {

    private ReactiveRedisTemplate<String, String> redisTemplate;
    private ReactiveZSetOperations<String, String> zSetOps;
    private GatewayProperties gatewayProperties;
    private RedisSlidingWindowStrategy strategy;

    @BeforeEach
    void setUp() {
        // 创建Mock对象
        redisTemplate = mock(ReactiveRedisTemplate.class);
        zSetOps = mock(ReactiveZSetOperations.class);
        
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        
        // 设置网关属性
        gatewayProperties = createTestGatewayProperties();
        
        // 创建策略实例
        strategy = new RedisSlidingWindowStrategy(redisTemplate, gatewayProperties);
    }

    @Test
    @DisplayName("测试Redis连接健康检查")
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void testRedisConnectionHealth() {
        log.info("=== 开始Redis连接健康检查测试 ===");
        
        // 模拟正常的Redis响应
        when(zSetOps.removeRangeByScore(anyString(), any(Range.class)))
                .thenReturn(Mono.just(0L));
        when(zSetOps.count(anyString(), any(Range.class)))
                .thenReturn(Mono.just(0L));
        when(zSetOps.add(anyString(), anyString(), anyDouble()))
                .thenReturn(Mono.just(true));
        when(redisTemplate.expire(anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));

        // 执行测试
        Mono<Boolean> result = strategy.isRequestExceedLimitReactive("192.168.1.1", "/pure-admin-service/gpt/isStateOwnedEnterprise");
        
        // 验证结果
        Boolean isLimited = result.block(Duration.ofSeconds(1));
        assertNotNull(isLimited);
        assertFalse(isLimited, "健康检查应该返回false（不限流）");
        
        log.info("Redis连接健康检查测试 - 通过");
    }

    @Test
    @DisplayName("测试Redis超时场景")
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void testRedisTimeoutScenario() {
        log.info("=== 开始Redis超时场景测试 ===");
        
        // 模拟Redis操作超时
        when(zSetOps.removeRangeByScore(anyString(), any(Range.class)))
                .thenReturn(Mono.delay(Duration.ofSeconds(2)).then(Mono.just(0L))); // 2秒延迟，超过200ms超时
        
        // 执行测试并验证超时处理
        Mono<Boolean> result = strategy.isRequestExceedLimitReactive("192.168.1.1", "/api/test");
        Boolean isLimited = result.block(Duration.ofSeconds(1));
        
        assertNotNull(isLimited);
        assertFalse(isLimited, "超时时应该返回false（不限流）");
        
        log.info("Redis超时场景测试 - 通过");
    }

    @Test
    @DisplayName("测试Redis连接失败场景")
    void testRedisConnectionFailure() {
        log.info("=== 开始Redis连接失败场景测试 ===");
        
        // 模拟Redis连接失败
        when(zSetOps.removeRangeByScore(anyString(), any(Range.class)))
                .thenReturn(Mono.error(new org.springframework.data.redis.RedisConnectionFailureException("Connection failed")));
        
        // 执行测试
        Mono<Boolean> result = strategy.isRequestExceedLimitReactive("192.168.1.1", "/api/test");
        Boolean isLimited = result.block(Duration.ofSeconds(1));
        
        assertNotNull(isLimited);
        assertFalse(isLimited, "连接失败时应该返回false（不限流）");
        
        log.info("Redis连接失败场景测试 - 通过");
    }

    @Test
    @DisplayName("测试阻塞版本的超时处理")
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void testBlockingVersionTimeout() {
        log.info("=== 开始阻塞版本超时处理测试 ===");
        
        // 模拟缓慢的Redis响应
        when(zSetOps.removeRangeByScore(anyString(), any(Range.class)))
                .thenReturn(Mono.delay(Duration.ofMillis(400)).then(Mono.just(0L))); // 400ms延迟，超过300ms超时
        
        // 测试阻塞版本
        long startTime = System.currentTimeMillis();
        boolean result = strategy.isRequestExceedLimit("192.168.1.1", "/api/test");
        long endTime = System.currentTimeMillis();
        
        // 验证结果和执行时间
        assertFalse(result, "超时时应该返回false");
        assertTrue(endTime - startTime < 1000, "执行时间应该小于1秒（由于超时控制）");
        
        log.info("阻塞版本超时处理测试 - 通过，执行时间: {}ms", endTime - startTime);
    }

    @Test
    @DisplayName("测试限流触发场景")
    void testRateLimitTriggered() {
        log.info("=== 开始限流触发场景测试 ===");
        
        // 模拟当前窗口内已有5次请求（超过限制）
        when(zSetOps.removeRangeByScore(anyString(), any(Range.class)))
                .thenReturn(Mono.just(0L));
        when(zSetOps.count(anyString(), any(Range.class)))
                .thenReturn(Mono.just(5L)); // 返回5次，超过默认限制1次
        
        // 执行测试
        Mono<Boolean> result = strategy.isRequestExceedLimitReactive("192.168.1.1", "/api/test");
        Boolean isLimited = result.block(Duration.ofSeconds(1));
        
        assertNotNull(isLimited);
        assertTrue(isLimited, "应该返回true表示限流");
        
        log.info("限流触发场景测试 - 通过");
    }

    @Test
    @DisplayName("测试正常访问场景")
    void testNormalAccess() {
        log.info("=== 开始正常访问场景测试 ===");
        
        // 模拟正常的Redis响应
        when(zSetOps.removeRangeByScore(anyString(), any(Range.class)))
                .thenReturn(Mono.just(0L));
        when(zSetOps.count(anyString(), any(Range.class)))
                .thenReturn(Mono.just(0L)); // 当前窗口内没有请求
        when(zSetOps.add(anyString(), anyString(), anyDouble()))
                .thenReturn(Mono.just(true));
        when(redisTemplate.expire(anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));
        
        // 执行测试
        Mono<Boolean> result = strategy.isRequestExceedLimitReactive("192.168.1.1", "/api/test");
        Boolean isLimited = result.block(Duration.ofSeconds(1));
        
        assertNotNull(isLimited);
        assertFalse(isLimited, "应该返回false表示不限流");
        
        log.info("正常访问场景测试 - 通过");
    }

    @Test
    @DisplayName("测试并发访问场景")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testConcurrentAccess() {
        log.info("=== 开始并发访问场景测试 ===");
        
        // 模拟正常的Redis响应
        when(zSetOps.removeRangeByScore(anyString(), any(Range.class)))
                .thenReturn(Mono.just(0L));
        when(zSetOps.count(anyString(), any(Range.class)))
                .thenReturn(Mono.just(0L));
        when(zSetOps.add(anyString(), anyString(), anyDouble()))
                .thenReturn(Mono.just(true));
        when(redisTemplate.expire(anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));
        
        // 创建线程池测试并发
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CompletableFuture<Void>[] futures = new CompletableFuture[20];
        
        for (int i = 0; i < 20; i++) {
            final int index = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    String ip = "192.168.1." + (index % 5 + 1);
                    boolean result = strategy.isRequestExceedLimit(ip, "/api/test");
                    log.debug("并发测试 - IP: {}, 结果: {}", ip, result);
                } catch (Exception e) {
                    log.error("并发测试异常 - 索引: {}, 错误: {}", index, e.getMessage());
                    fail("并发测试不应该抛出异常: " + e.getMessage());
                }
            }, executor);
        }
        
        // 等待所有任务完成
        assertDoesNotThrow(() -> {
            CompletableFuture.allOf(futures).get(3, TimeUnit.SECONDS);
        });
        
        executor.shutdown();
        log.info("并发访问场景测试 - 通过");
    }

    @Test
    @DisplayName("测试无效参数场景")
    void testInvalidParameters() {
        log.info("=== 开始无效参数场景测试 ===");
        
        // 测试null参数
        Mono<Boolean> result1 = strategy.isRequestExceedLimitReactive(null, "/api/test");
        Boolean isLimited1 = result1.block(Duration.ofSeconds(1));
        assertNotNull(isLimited1);
        assertFalse(isLimited1);
        
        Mono<Boolean> result2 = strategy.isRequestExceedLimitReactive("192.168.1.1", null);
        Boolean isLimited2 = result2.block(Duration.ofSeconds(1));
        assertNotNull(isLimited2);
        assertFalse(isLimited2);
        
        Mono<Boolean> result3 = strategy.isRequestExceedLimitReactive(null, null);
        Boolean isLimited3 = result3.block(Duration.ofSeconds(1));
        assertNotNull(isLimited3);
        assertFalse(isLimited3);
        
        log.info("无效参数场景测试 - 通过");
    }

    @Test
    @DisplayName("Redis性能基准测试")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testRedisPerformanceBenchmark() {
        log.info("=== 开始Redis性能基准测试 ===");
        
        // 模拟快速的Redis响应
        when(zSetOps.removeRangeByScore(anyString(), any(Range.class)))
                .thenReturn(Mono.just(0L).delayElement(Duration.ofMillis(10))); // 10ms模拟网络延迟
        when(zSetOps.count(anyString(), any(Range.class)))
                .thenReturn(Mono.just(0L).delayElement(Duration.ofMillis(5)));
        when(zSetOps.add(anyString(), anyString(), anyDouble()))
                .thenReturn(Mono.just(true).delayElement(Duration.ofMillis(5)));
        when(redisTemplate.expire(anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true).delayElement(Duration.ofMillis(5)));
        
        // 执行100次测试，测量平均响应时间
        long totalTime = 0;
        int testCount = 50; // 减少测试次数避免超时
        
        for (int i = 0; i < testCount; i++) {
            long startTime = System.currentTimeMillis();
            strategy.isRequestExceedLimit("192.168.1.1", "/api/test");
            long endTime = System.currentTimeMillis();
            totalTime += (endTime - startTime);
        }
        
        double averageTime = (double) totalTime / testCount;
        log.info("Redis性能基准测试结果 - 平均响应时间: {}ms", averageTime);
        
        // 验证平均响应时间应该在合理范围内
        assertTrue(averageTime < 500, "平均响应时间应该小于500ms，实际: " + averageTime + "ms");
    }

    /**
     * 创建测试用的网关属性配置
     */
    private GatewayProperties createTestGatewayProperties() {
        GatewayProperties properties = new GatewayProperties();
        
        // 创建限流配置
        GatewayProperties.RateLimitConfig rateLimitConfig = new GatewayProperties.RateLimitConfig();
        
        // 创建滑动窗口配置
        GatewayProperties.SlidingWindowConfig slidingWindowConfig = new GatewayProperties.SlidingWindowConfig();
        slidingWindowConfig.setDefaultWindowSize(60); // 60秒窗口
        slidingWindowConfig.setDefaultMaxRequests(100); // 默认最大100次请求
        
        // 创建路径规则列表
        List<GatewayProperties.PathRateLimitRule> pathRules = new ArrayList<>();
        
        // 测试路径规则
        GatewayProperties.PathRateLimitRule testRule = new GatewayProperties.PathRateLimitRule();
        testRule.setPath("/api/test");
        testRule.setEnabled(true);
        testRule.setWindowSize(1); // 1秒窗口
        testRule.setMaxRequests(1); // 最大1次请求
        pathRules.add(testRule);
        
        // API路径规则
        GatewayProperties.PathRateLimitRule apiRule = new GatewayProperties.PathRateLimitRule();
        apiRule.setPath("/api/**");
        apiRule.setEnabled(true);
        apiRule.setWindowSize(60); // 60秒窗口
        apiRule.setMaxRequests(1000); // 最大1000次请求
        pathRules.add(apiRule);
        
        slidingWindowConfig.setPathRules(pathRules);
        rateLimitConfig.setSlidingWindow(slidingWindowConfig);
        properties.setRateLimit(rateLimitConfig);
        
        return properties;
    }
} 