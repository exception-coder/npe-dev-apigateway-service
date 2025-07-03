package com.dev.gateway.logging.filter;

import com.dev.gateway.logging.properties.LoggingProperties;
import com.dev.gateway.logging.service.LoggingService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

/**
 * 响应日志网关过滤器工厂
 * 用于在路由配置中引用：ResponseLogger
 */
@Component
@Slf4j
public class ResponseLoggerGatewayFilterFactory extends AbstractGatewayFilterFactory<ResponseLoggerGatewayFilterFactory.Config> {

    private final LoggingService loggingService;
    private final LoggingProperties loggingProperties;

    public ResponseLoggerGatewayFilterFactory(LoggingService loggingService, LoggingProperties loggingProperties) {
        super(Config.class);
        this.loggingService = loggingService;
        this.loggingProperties = loggingProperties;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // 检查是否启用响应体日志
            if (!loggingProperties.isResponseBodyEnabled()) {
                return chain.filter(exchange);
            }

            long startTime = System.currentTimeMillis();
            
            return chain.filter(exchange)
                    .doFinally(signalType -> {
                        try {
                            // 记录响应日志（无响应体）
                            loggingService.logResponse(exchange, "");
                            
                            // 记录访问日志
                            long duration = System.currentTimeMillis() - startTime;
                            loggingService.logAccess(exchange, duration);
                        } catch (Exception e) {
                            log.error("响应日志记录失败", e);
                        }
                    })
                    .doOnError(throwable -> {
                        log.error("响应处理失败", throwable);
                    });
        };
    }

    @Data
    public static class Config {
        // 可以在配置中添加参数
        private boolean includeHeaders = true;
        private boolean includeResponseBody = false;
    }
} 