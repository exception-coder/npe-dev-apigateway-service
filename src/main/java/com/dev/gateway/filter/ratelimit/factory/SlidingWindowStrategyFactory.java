package com.dev.gateway.filter.ratelimit.factory;

import com.dev.gateway.properties.GatewayProperties;
import com.dev.gateway.filter.ratelimit.strategy.LocalMemorySlidingWindowStrategy;
import com.dev.gateway.filter.ratelimit.strategy.RedisSlidingWindowStrategy;
import com.dev.gateway.filter.ratelimit.strategy.SlidingWindowStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 滑动窗口策略工厂
 * 根据配置选择使用本地内存或Redis存储策略
 */
@Slf4j
@Component
public class SlidingWindowStrategyFactory {

    private final GatewayProperties gatewayProperties;
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public SlidingWindowStrategyFactory(GatewayProperties gatewayProperties,
                                        @Autowired(required = false) ReactiveRedisTemplate<String, String> redisTemplate) {
        this.gatewayProperties = gatewayProperties;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 创建滑动窗口策略实例
     *
     * @return 策略实例
     */
    public SlidingWindowStrategy createStrategy() {
        // 获取存储类型配置
        String storageType = getStorageType();
        
        switch (storageType.toUpperCase()) {
            case "REDIS":
                if (redisTemplate != null) {
                    log.info("创建Redis滑动窗口策略");
                    return new RedisSlidingWindowStrategy(redisTemplate, gatewayProperties);
                } else {
                    log.warn("Redis模板未配置，回退到本地内存策略");
                    return new LocalMemorySlidingWindowStrategy(gatewayProperties);
                }
            case "LOCAL_MEMORY":
            default:
                log.info("创建本地内存滑动窗口策略");
                return new LocalMemorySlidingWindowStrategy(gatewayProperties);
        }
    }

    /**
     * 获取存储类型配置
     */
    private String getStorageType() {
        if (gatewayProperties != null &&
                gatewayProperties.getRateLimit() != null &&
                gatewayProperties.getRateLimit().getSlidingWindow() != null) {
            return gatewayProperties.getRateLimit().getSlidingWindow().getStorageType();
        }
        return "LOCAL_MEMORY"; // 默认使用本地内存
    }
} 