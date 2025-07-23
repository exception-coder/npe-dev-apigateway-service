package com.dev.gateway.filter.ratelimit.strategy;

import cn.hutool.core.date.DateUtil;
import com.dev.gateway.cache.DdosIpCache;
import com.dev.gateway.properties.GatewayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.AntPathMatcher;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * 基于本地内存的滑动窗口限流策略实现
 * 使用JVM内存存储访问记录，适用于单机部署场景
 */
@Slf4j
public class LocalMemorySlidingWindowStrategy implements SlidingWindowStrategy {

    // 每个IP+路径组合的访问窗口记录，使用Deque存储
    private final Map<String, Deque<Integer>> ipPathAccessLog = new HashMap<>();

    // Spring路径匹配器，支持通配符匹配
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // 网关配置属性
    private GatewayProperties gatewayProperties;

    public LocalMemorySlidingWindowStrategy(GatewayProperties gatewayProperties) {
        this.gatewayProperties = gatewayProperties;
        log.info("初始化本地内存滑动窗口策略");
    }

    @Override
    public boolean isRequestExceedLimit(String ip, String requestPath) {
        if (ip == null || requestPath == null) {
            return false;
        }

        int currentSecond = getCurrentSecond();

        // 获取该路径的限流规则
        PathLimitRule rule = getPathLimitRule(requestPath);

        // 生成IP+路径的组合键
        String ipPathKey = ip + ":" + requestPath;

        // 如果记录不存在，初始化
        ipPathAccessLog.putIfAbsent(ipPathKey, new LinkedList<>());

        Deque<Integer> accessTimes = ipPathAccessLog.get(ipPathKey);

        try {
            // 移除超过窗口时间的过期访问记录
            while (!accessTimes.isEmpty() && currentSecond - accessTimes.peekFirst() >= rule.windowSize) {
                accessTimes.pollFirst();  // 移除最早的访问记录
            }

            // 检查访问次数是否超过阈值
            if (accessTimes.size() >= rule.maxRequests) {
                String formatDateTime = DateUtil.formatDateTime(DateUtil.date());
                // 记录到DDOS缓存（使用IP+路径作为键）
                DdosIpCache.SLIDING_WINDOW_IPTRACKER.put(ipPathKey, formatDateTime);
                log.warn("检测到限流触发 [本地内存] - IP: {}, 路径: {}, 窗口: {}秒, 限制: {}次, 当前: {}次",
                        ip, requestPath, rule.windowSize, rule.maxRequests, accessTimes.size());
                return true;  // 超过限流阈值
            }

            // 如果当前秒还没有记录，则添加
            if (accessTimes.isEmpty() || accessTimes.peekLast() != currentSecond) {
                accessTimes.addLast(currentSecond);
            }

            log.debug("正常访问记录 [本地内存] - IP: {}, 路径: {}, 窗口内访问次数: {}/{}",
                    ip, requestPath, accessTimes.size(), rule.maxRequests);

        } catch (Exception e) {
            log.error("本地内存滑动窗口多线程操作异常 - IP: {}, 路径: {}, 异常: {}, 访问记录: {}",
                    ip, requestPath, e.getMessage(), accessTimes);
        }

        return false;
    }

    @Override
    public void cleanupExpiredRecords() {
        int currentSecond = getCurrentSecond();
        int maxWindowSize = getMaxWindowSize();

        ipPathAccessLog.entrySet().removeIf(entry -> {
            Deque<Integer> accessTimes = entry.getValue();
            // 移除所有过期记录
            while (!accessTimes.isEmpty() && currentSecond - accessTimes.peekFirst() >= maxWindowSize) {
                accessTimes.pollFirst();
            }
            // 如果队列为空，移除整个条目
            return accessTimes.isEmpty();
        });

        log.debug("本地内存滑动窗口过期记录清理完成，当前记录数: {}", ipPathAccessLog.size());
    }

    @Override
    public String getStrategyType() {
        return "LOCAL_MEMORY";
    }

    /**
     * 获取当前的秒级时间戳
     */
    private int getCurrentSecond() {
        return (int) LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
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
                log.debug("路径匹配成功 [本地内存] - 模式: {}, 路径: {}, 窗口: {}秒, 限制: {}次",
                        pathPattern, requestPath, rule.getWindowSize(), rule.getMaxRequests());
                return new PathLimitRule(rule.getWindowSize(), rule.getMaxRequests());
            }
        }

        // 没有匹配的规则，使用默认规则
        log.debug("使用默认限流规则 [本地内存] - 路径: {}, 窗口: {}秒, 限制: {}次",
                requestPath, config.getDefaultWindowSize(), config.getDefaultMaxRequests());
        return new PathLimitRule(config.getDefaultWindowSize(), config.getDefaultMaxRequests());
    }

    /**
     * 获取配置中最大的窗口时间，用于清理过期记录
     */
    private int getMaxWindowSize() {
        if (gatewayProperties == null ||
                gatewayProperties.getRateLimit() == null ||
                gatewayProperties.getRateLimit().getSlidingWindow() == null) {
            return 60; // 默认60秒
        }

        GatewayProperties.SlidingWindowConfig config = gatewayProperties.getRateLimit().getSlidingWindow();
        int maxWindowSize = config.getDefaultWindowSize();

        for (GatewayProperties.PathRateLimitRule rule : config.getPathRulesMap().values()) {
            if (rule.isEnabled()) {
                maxWindowSize = Math.max(maxWindowSize, rule.getWindowSize());
            }
        }

        return maxWindowSize;
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