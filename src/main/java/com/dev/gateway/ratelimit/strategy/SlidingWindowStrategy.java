package com.dev.gateway.ratelimit.strategy;

import reactor.core.publisher.Mono;

/**
 * 滑动窗口限流策略接口
 * 定义了滑动窗口限流的基本操作
 */
public interface SlidingWindowStrategy {

    /**
     * 检查IP+路径组合是否超过限流阈值
     *
     * @param ip          客户端IP
     * @param requestPath 请求路径
     * @return true表示超过限流阈值，false表示正常
     */
    boolean isRequestExceedLimit(String ip, String requestPath);

    /**
     * 响应式版本：检查IP+路径组合是否超过限流阈值
     * 推荐在WebFlux环境中使用此方法，避免阻塞操作
     *
     * @param ip          客户端IP
     * @param requestPath 请求路径
     * @return Mono<Boolean> true表示超过限流阈值，false表示正常
     */
    default Mono<Boolean> isRequestExceedLimitReactive(String ip, String requestPath) {
        // 默认实现：调用阻塞版本并包装为Mono
        // 子类应该重写此方法提供真正的响应式实现
        try {
            boolean result = isRequestExceedLimit(ip, requestPath);
            return Mono.just(result);
        } catch (Exception e) {
            return Mono.just(false); // 异常时不限流
        }
    }

    /**
     * 清理过期的访问记录
     */
    void cleanupExpiredRecords();

    /**
     * 获取策略类型名称
     *
     * @return 策略类型
     */
    String getStrategyType();
} 