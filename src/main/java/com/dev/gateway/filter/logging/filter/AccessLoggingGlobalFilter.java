package com.dev.gateway.filter.logging.filter;

import com.dev.gateway.filter.GlobalFilterOrderConfig;
import com.dev.gateway.filter.logging.properties.LoggingProperties;
import com.dev.gateway.filter.logging.service.LoggingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 访问日志全局过滤器
 * 用于记录请求的访问信息和响应时间
 */
@Slf4j
@Component
public class AccessLoggingGlobalFilter implements GlobalFilter, Ordered {

    private final LoggingProperties loggingProperties;

    private final LoggingService loggingService;

    public AccessLoggingGlobalFilter(LoggingProperties loggingProperties, LoggingService loggingService) {
        this.loggingProperties = loggingProperties;
        this.loggingService = loggingService;
    }


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 检查是否启用访问日志
        if (!loggingProperties.isAccessLogEnabled()) {
            return chain.filter(exchange);
        }

        String requestUri = exchange.getRequest().getURI().getPath();
        
        // 检查是否跳过日志记录
        if (loggingService.shouldSkipLogging(requestUri)) {
            return chain.filter(exchange);
        }

        // 记录开始时间
        long startTime = System.currentTimeMillis();

        return chain.filter(exchange)
                .doFinally(signalType -> {
                    // 计算请求处理时间
                    long duration = System.currentTimeMillis() - startTime;
                    
                    // 记录访问日志
                    loggingService.logAccess(exchange, duration);
                });
    }

    @Override
    public int getOrder() {
        return GlobalFilterOrderConfig.ACCESS_LOGGING_FILTER_ORDER; // 使用统一的全局过滤器顺序管理
    }
} 