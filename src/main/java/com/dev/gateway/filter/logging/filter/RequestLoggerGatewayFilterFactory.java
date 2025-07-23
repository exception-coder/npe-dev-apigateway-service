package com.dev.gateway.filter.logging.filter;

import com.dev.gateway.filter.logging.properties.LoggingProperties;
import com.dev.gateway.filter.logging.service.LoggingService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

/**
 * 请求日志网关过滤器工厂
 * 用于在路由配置中引用：RequestLogger
 */
@Component
@Slf4j
public class RequestLoggerGatewayFilterFactory extends AbstractGatewayFilterFactory<RequestLoggerGatewayFilterFactory.Config> {

    private final LoggingService loggingService;
    private final LoggingProperties loggingProperties;

    public RequestLoggerGatewayFilterFactory(LoggingService loggingService, LoggingProperties loggingProperties) {
        super(Config.class);
        this.loggingService = loggingService;
        this.loggingProperties = loggingProperties;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // 检查是否启用请求体日志
            if (!loggingProperties.isRequestBodyEnabled()) {
                // 如果没启用请求体日志，至少记录基本的访问日志
                long startTime = System.currentTimeMillis();
                return chain.filter(exchange)
                        .doFinally(signalType -> {
                            long duration = System.currentTimeMillis() - startTime;
                            loggingService.logAccess(exchange, duration);
                        });
            }

            // 记录请求日志（无请求体）
            try {
                loggingService.logRequest(exchange, "");
            } catch (Exception e) {
                log.error("请求日志记录失败", e);
            }

            return chain.filter(exchange)
                    .doOnError(throwable -> {
                        log.error("请求处理失败", throwable);
                    });
        };
    }

    @Data
    public static class Config {
        // 可以在配置中添加参数
        private boolean includeHeaders = true;
        private boolean includePayload = false;
    }
} 