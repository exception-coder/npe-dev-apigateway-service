package com.dev.gateway.filter.ratelimit.strategy;

import cn.hutool.core.date.DateUtil;
import com.dev.gateway.cache.DdosIpCache;
import com.dev.gateway.properties.GatewayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.util.AntPathMatcher;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 基于Redis的滑动窗口限流策略实现
 * 使用Redis Sorted Set (ZSET) 实现真正的滑动窗口，支持分布式部署
 * 每个请求作为ZSET的一个元素，score为时间戳，实现精确的时间窗口滑动
 */
@Slf4j
public class RedisSlidingWindowStrategy implements SlidingWindowStrategy {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    // Spring路径匹配器，支持通配符匹配
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // 网关配置属性
    private GatewayProperties gatewayProperties;

    // Redis键前缀
    private static final String REDIS_KEY_PREFIX = "sliding_window:";

    // Lua脚本：滑动窗口限流检查和记录（原子操作）
    private static final String LUA_SCRIPT = 
        "local key = KEYS[1]\n" +
        "local windowStart = tonumber(ARGV[1])\n" +
        "local currentTime = tonumber(ARGV[2])\n" +
        "local maxRequests = tonumber(ARGV[3])\n" +
        "local windowSize = tonumber(ARGV[4])\n" +
        "\n" +
        "-- 清理过期记录\n" +
        "redis.call('ZREMRANGEBYSCORE', key, 0, windowStart)\n" +
        "\n" +
        "-- 统计当前窗口内的请求数\n" +
        "local currentCount = redis.call('ZCARD', key)\n" +
        "\n" +
        "-- 检查是否超过限制\n" +
        "if currentCount >= maxRequests then\n" +
        "    return {1, currentCount}\n" +
        "end\n" +
        "\n" +
        "-- 记录当前请求\n" +
        "redis.call('ZADD', key, currentTime, tostring(currentTime))\n" +
        "\n" +
        "-- 设置过期时间\n" +
        "redis.call('EXPIRE', key, windowSize + 60)\n" +
        "\n" +
        "-- 返回结果：{是否限流(0/1), 当前计数}\n" +
        "return {0, currentCount + 1}";

    private final DefaultRedisScript<List> redisScript;

    public RedisSlidingWindowStrategy(ReactiveRedisTemplate<String, String> redisTemplate, 
                                      GatewayProperties gatewayProperties) {
        this.redisTemplate = redisTemplate;
        this.gatewayProperties = gatewayProperties;
        
        // 初始化Lua脚本
        this.redisScript = new DefaultRedisScript<>();
        this.redisScript.setScriptText(LUA_SCRIPT);
        this.redisScript.setResultType(List.class);
        
        log.info("初始化Redis滑动窗口策略（ZSET+Lua脚本实现）");
    }

    @Override
    public boolean isRequestExceedLimit(String ip, String requestPath) {
        // 为了保持接口兼容性，使用响应式方法并阻塞获取结果
        try {
            return isRequestExceedLimitReactive(ip, requestPath)
                    .timeout(Duration.ofMillis(1000)) // 增加超时时间到1秒，给Lua脚本足够执行时间
                    .doOnError(throwable -> {
                        // 分类错误日志
                        if (throwable instanceof java.util.concurrent.TimeoutException) {
                            log.warn("Redis滑动窗口超时 - IP: {}, 路径: {}, 超时时间: 1000ms", ip, requestPath);
                        } else {
                            log.error("Redis滑动窗口异常 - IP: {}, 路径: {}, 错误: {}", ip, requestPath, throwable.getMessage());
                        }
                    })
                    .onErrorReturn(false) // 出错时不限流，保证服务可用性
                    .block();
        } catch (Exception e) {
            log.error("Redis滑动窗口检查阻塞异常 - IP: {}, 路径: {}, 错误: {}", ip, requestPath, e.getMessage());
            return false; // 超时或异常时不限流，保证服务可用性
        }
    }

    /**
     * 响应式版本的限流检查方法
     */
    public Mono<Boolean> isRequestExceedLimitReactive(String ip, String requestPath) {
        if (ip == null || requestPath == null) {
            return Mono.just(false);
        }

        try {
            // 获取该路径的限流规则
            PathLimitRule rule = getPathLimitRule(requestPath);
            
            // 生成Redis键（滑动窗口使用ZSET）
            String redisKey = REDIS_KEY_PREFIX + ip + ":" + requestPath;
            
            long currentTime = System.currentTimeMillis();
            long windowStartTime = currentTime - rule.windowSize * 1000L; // 转换为毫秒
            
            // 记录开始时间用于诊断
            long operationStartTime = System.currentTimeMillis();
            
            // 使用Lua脚本原子操作，并指定调度器
            return performRedisOperationsWithLua(redisKey, currentTime, windowStartTime, rule, ip, requestPath)
                    .subscribeOn(Schedulers.boundedElastic()) // 使用专门的调度器
                    .timeout(Duration.ofMillis(500)) // 单个Lua脚本执行，500ms应该足够
                    .doOnSuccess(result -> {
                        long totalTime = System.currentTimeMillis() - operationStartTime;
                        if (totalTime > 100) {
                            log.warn("Redis操作耗时较长 - IP: {}, 路径: {}, 耗时: {}ms", ip, requestPath, totalTime);
                        } else {
                            log.debug("Redis操作正常 - IP: {}, 路径: {}, 耗时: {}ms", ip, requestPath, totalTime);
                        }
                    })
                    .onErrorResume(throwable -> {
                        long totalTime = System.currentTimeMillis() - operationStartTime;
                        
                        // 分类处理不同类型的异常
                        if (throwable instanceof java.util.concurrent.TimeoutException) {
                            log.warn("Redis Lua脚本超时 - IP: {}, 路径: {}, 耗时: {}ms, 超时阈值: 500ms", ip, requestPath, totalTime);
                            log.warn("可能原因：1)Redis服务器负载高 2)网络延迟 3)连接池耗尽 4)响应式流背压");
                        } else if (throwable instanceof org.springframework.data.redis.RedisConnectionFailureException) {
                            log.error("Redis连接失败 - IP: {}, 路径: {}, 耗时: {}ms, 错误: {}", ip, requestPath, totalTime, throwable.getMessage());
                        } else if (throwable.getMessage().contains("Pool exhausted")) {
                            log.error("Redis连接池耗尽 - IP: {}, 路径: {}, 耗时: {}ms", ip, requestPath, totalTime);
                            log.error("建议检查连接池配置：max-active, max-wait, max-idle");
                        } else {
                            log.error("Redis滑动窗口Lua脚本异常 - IP: {}, 路径: {}, 耗时: {}ms, 异常: {}", ip, requestPath, totalTime, throwable.getMessage());
                        }
                        
                        // 提供诊断信息
                        if (totalTime > 1000) {
                            log.error("严重延迟检测 - 建议检查:");
                            log.error("1. Redis服务器状态: redis-cli --latency-history");
                            log.error("2. 网络连接: ping redis-host");
                            log.error("3. 连接池状态和配置");
                            log.error("4. 应用线程池状态");
                        }
                        
                        // Redis异常时不阻断请求，返回false表示不限流
                        return Mono.just(false);
                    });
            
        } catch (Exception e) {
            log.error("Redis滑动窗口预处理异常 - IP: {}, 路径: {}, 异常: {}", ip, requestPath, e.getMessage(), e);
            // Redis异常时不阻断请求，返回false表示不限流
            return Mono.just(false);
        }
    }

    /**
     * 使用Lua脚本执行Redis操作的优化方法（原子操作）
     */
    private Mono<Boolean> performRedisOperationsWithLua(String redisKey, long currentTime, long windowStartTime, 
                                                       PathLimitRule rule, String ip, String requestPath) {
        
        List<String> keys = Arrays.asList(redisKey);
        List<String> args = Arrays.asList(
            String.valueOf(windowStartTime),  // ARGV[1]: 窗口开始时间
            String.valueOf(currentTime),      // ARGV[2]: 当前时间
            String.valueOf(rule.maxRequests), // ARGV[3]: 最大请求数
            String.valueOf(rule.windowSize)   // ARGV[4]: 窗口大小
        );
        
        return redisTemplate.execute(redisScript, keys, args)
                .next() // 将Flux转换为Mono，取第一个结果
                .cast(List.class)
                .map(result -> {
                    if (result != null && result.size() >= 2) {
                        Long isLimited =  (Long)result.get(0);
                        Long currentCount = (Long) result.get(1);
                        
                        if (isLimited == 1) {
                            // 触发限流
                            String formatDateTime = DateUtil.formatDateTime(DateUtil.date());
                            DdosIpCache.SLIDING_WINDOW_IPTRACKER.put(ip + ":" + requestPath, formatDateTime);
                            log.warn("检测到限流触发 [Redis滑动窗口Lua] - IP: {}, 路径: {}, 窗口: {}秒, 限制: {}次, 当前: {}次",
                                    ip, requestPath, rule.windowSize, rule.maxRequests, currentCount);
                            return true;
                        } else {
                            // 正常通过
                            log.debug("正常访问记录 [Redis滑动窗口Lua] - IP: {}, 路径: {}, 窗口内访问次数: {}/{}",
                                    ip, requestPath, currentCount, rule.maxRequests);
                            return false;
                        }
                    } else {
                        log.warn("Redis Lua脚本返回结果异常 - IP: {}, 路径: {}, 结果: {}", ip, requestPath, result);
                        return false;
                    }
                })
                .defaultIfEmpty(false);
    }

    @Override
    public void cleanupExpiredRecords() {
        // Redis键会自动过期，无需手动清理
        log.debug("Redis滑动窗口清理：使用自动过期机制，无需手动清理");
    }

    @Override
    public String getStrategyType() {
        return "REDIS";
    }

    /**
     * 获取指定路径的限流规则
     *
     * @param requestPath 请求路径
     * @return 匹配的限流规则
     */
    private PathLimitRule getPathLimitRule(String requestPath) {
        if (gatewayProperties == null ||
                gatewayProperties.getRateLimit() == null ||
                gatewayProperties.getRateLimit().getSlidingWindow() == null) {
            // 如果配置为空，使用默认规则
            return new PathLimitRule(1, 1);
        }

        GatewayProperties.SlidingWindowConfig config = gatewayProperties.getRateLimit().getSlidingWindow();

        // 遍历配置的路径规则，找到匹配的规则
        for (Map.Entry<String, GatewayProperties.PathRateLimitRule> entry : config.getPathRulesMap().entrySet()) {
            String pathPattern = entry.getKey();
            GatewayProperties.PathRateLimitRule rule = entry.getValue();

            // 使用Spring AntPathMatcher进行路径匹配
            if (rule.isEnabled() && pathMatcher.match(pathPattern, requestPath)) {
                log.debug("路径匹配成功 [Redis滑动窗口] - 模式: {}, 路径: {}, 窗口: {}秒, 限制: {}次",
                        pathPattern, requestPath, rule.getWindowSize(), rule.getMaxRequests());
                return new PathLimitRule(rule.getWindowSize(), rule.getMaxRequests());
            }
        }

        // 没有匹配的规则，使用默认规则
        log.debug("使用默认限流规则 [Redis滑动窗口] - 路径: {}, 窗口: {}秒, 限制: {}次",
                requestPath, config.getDefaultWindowSize(), config.getDefaultMaxRequests());
        return new PathLimitRule(config.getDefaultWindowSize(), config.getDefaultMaxRequests());
    }

    /**
     * 路径限流规则内部类
     */
    private static class PathLimitRule {
        final int windowSize;
        final int maxRequests;

        PathLimitRule(int windowSize, int maxRequests) {
            this.windowSize = windowSize;
            this.maxRequests = maxRequests;
        }
    }
} 