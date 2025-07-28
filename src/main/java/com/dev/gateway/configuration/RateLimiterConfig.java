package com.dev.gateway.configuration;

import com.dev.gateway.properties.GatewayProperties;
import com.dev.gateway.filter.ratelimit.factory.SlidingWindowStrategyFactory;
import com.dev.gateway.filter.ratelimit.strategy.SlidingWindowStrategy;
import com.dev.gateway.service.IpResolverService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.ObjectUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;

@Slf4j
@Configuration
@EnableScheduling
public class RateLimiterConfig {

    /**
     * 滑动窗口IP跟踪器实例
     */
    public static final SlidingWindowIpTracker SLIDING_WINDOW_IP_TRACKER = new SlidingWindowIpTracker();

    private final GatewayProperties gatewayProperties;
    private final SlidingWindowStrategyFactory strategyFactory;
    private final IpResolverService ipResolverService;

    public RateLimiterConfig(GatewayProperties gatewayProperties, SlidingWindowStrategyFactory strategyFactory,
            IpResolverService ipResolverService) {
        this.gatewayProperties = gatewayProperties;
        this.strategyFactory = strategyFactory;
        this.ipResolverService = ipResolverService;
    }

    /**
     * 在Bean初始化后设置滑动窗口跟踪器的配置属性
     */
    @PostConstruct
    public void initSlidingWindowTracker() {
        if (gatewayProperties != null && strategyFactory != null) {
            // 创建策略实例
            SlidingWindowStrategy strategy = strategyFactory.createStrategy();
            SLIDING_WINDOW_IP_TRACKER.setStrategy(strategy);
            log.info("滑动窗口跟踪器配置初始化完成，使用策略: {}", strategy.getStrategyType());
        }
    }

    /**
     * 定时清理滑动窗口中的过期访问记录
     * 每5分钟执行一次
     */
    @Scheduled(fixedRate = 300000) // 5分钟 = 300000毫秒
    public void cleanupExpiredSlidingWindowRecords() {
        try {
            SLIDING_WINDOW_IP_TRACKER.cleanupExpiredRecords();
            log.debug("滑动窗口过期记录清理任务执行完成");
        } catch (Exception e) {
            log.error("滑动窗口过期记录清理任务执行失败: {}", e.getMessage(), e);
        }
    }

    @Primary
    @Bean
    public RedisRateLimiter ipRedisRateLimiter() {
        RedisRateLimiter redisRateLimiter = new RedisRateLimiter(5, 5);
        return redisRateLimiter;
    }

    @Bean
    public RedisRateLimiter prefixIpRedisRateLimiter() {
        return new RedisRateLimiter(20, 25);
    }

    @Bean
    public KeyResolver myKeyResolver() {
        return new KeyResolver() {
            @Override
            public Mono<String> resolve(ServerWebExchange exchange) {
                ServerHttpRequest serverHttpRequest = exchange.getRequest();

                // 获取浏览器指纹
                String browserFingerprint = serverHttpRequest.getHeaders().getFirst("x-browser-fingerprint");
                if (ObjectUtils.isEmpty(browserFingerprint)) {
                    browserFingerprint = "unknown";
                }

                // 获取客户端IP，使用统一的IpResolverService
                String clientIp = ipResolverService.getClientIp(exchange);

                // 拼接浏览器指纹和客户端IP作为限流键
                String rateLimitKey = browserFingerprint + ":" + clientIp;

                return Mono.just(rateLimitKey);
            }
        };
    }

    @Data
    public static class SlidingWindowIpTracker {

        // 滑动窗口策略实例
        private SlidingWindowStrategy strategy;

        /**
         * 设置滑动窗口策略
         */
        public void setStrategy(SlidingWindowStrategy strategy) {
            this.strategy = strategy;
        }

        /**
         * 检查IP+路径组合是否超过限流阈值
         *
         * @param ip          客户端IP
         * @param requestPath 请求路径
         * @return true表示超过限流阈值，false表示正常
         */
        public boolean isRequestExceedLimit(String ip, String requestPath) {
            if (strategy == null) {
                log.warn("滑动窗口策略未初始化，跳过限流检查");
                return false;
            }
            return strategy.isRequestExceedLimit(ip, requestPath);
        }

        /**
         * 响应式版本：检查IP+路径组合是否超过限流阈值
         * 推荐在WebFlux环境中使用此方法
         *
         * @param ip          客户端IP
         * @param requestPath 请求路径
         * @return Mono<Boolean> true表示超过限流阈值，false表示正常
         */
        public Mono<Boolean> isRequestExceedLimitReactive(String ip, String requestPath) {
            if (strategy == null) {
                log.warn("滑动窗口策略未初始化，跳过限流检查");
                return Mono.just(false);
            }
            return strategy.isRequestExceedLimitReactive(ip, requestPath);
        }

        /**
         * 清理过期的访问记录（可以定期调用）
         */
        public void cleanupExpiredRecords() {
            if (strategy != null) {
                strategy.cleanupExpiredRecords();
            }
        }

        /**
         * 获取当前使用的策略类型
         */
        public String getStrategyType() {
            return strategy != null ? strategy.getStrategyType() : "UNKNOWN";
        }
    }

}