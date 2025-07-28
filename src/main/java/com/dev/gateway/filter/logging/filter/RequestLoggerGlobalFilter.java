package com.dev.gateway.filter.logging.filter;

import com.dev.gateway.filter.GlobalFilterOrderConfig;
import com.dev.gateway.filter.logging.properties.LoggingProperties;
import com.dev.gateway.filter.logging.service.LoggingService;
import com.dev.gateway.service.IpResolverService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 请求日志全局过滤器
 * 用于记录和处理请求体内容
 */
@Slf4j
@Component
public class RequestLoggerGlobalFilter implements GlobalFilter, Ordered {

    private final LoggingProperties loggingProperties;

    private final LoggingService loggingService;

    private final IpResolverService ipResolverService;

    private final Consumer<SignalType> onFinally = Objects.requireNonNull(signalType -> {
        MDC.remove("requestId");
        MDC.remove("clientIp");
        MDC.remove("requestPath");
    });

    public RequestLoggerGlobalFilter(LoggingProperties loggingProperties, LoggingService loggingService,
            IpResolverService ipResolverService) {
        this.loggingProperties = loggingProperties;
        this.loggingService = loggingService;
        this.ipResolverService = ipResolverService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 检查是否启用请求体日志
        if (!loggingProperties.isRequestBodyEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String requestUri = request.getURI().getPath();

        // 检查是否跳过日志记录
        if (loggingService.shouldSkipLogging(requestUri)) {
            return chain.filter(exchange);
        }

        // 设置MDC上下文
        String requestId = java.util.UUID.randomUUID().toString().substring(0, 8);
        String clientIp = ipResolverService.getClientIp(exchange);
        MDC.put("requestId", requestId);
        MDC.put("clientIp", clientIp);
        MDC.put("requestPath", requestUri);

        // 只处理有请求体的请求
        if (!shouldLogRequestBody(request)) {
            return chain.filter(exchange).doFinally(onFinally);
        }

        return processRequestBody(exchange, chain);
    }

    /**
     * 处理请求体
     */
    private Mono<Void> processRequestBody(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        return DataBufferUtils.join(request.getBody())
                .cast(DataBuffer.class)
                .map(dataBuffer -> {
                    String requestBody = dataBuffer.toString(StandardCharsets.UTF_8);
                    DataBufferUtils.release(dataBuffer);

                    // 记录请求日志
                    loggingService.logRequest(exchange, requestBody);

                    // 创建新的请求体
                    return requestBody;
                })
                .defaultIfEmpty("")
                .flatMap(requestBody -> {
                    // 重新构建请求，包含原始请求体
                    ServerHttpRequestDecorator decorator = new ServerHttpRequestDecorator(request) {
                        @Override
                        public Flux<DataBuffer> getBody() {
                            if (StringUtils.hasText(requestBody)) {
                                DataBuffer buffer = exchange.getResponse().bufferFactory()
                                        .wrap(requestBody.getBytes(StandardCharsets.UTF_8));
                                return Flux.just(buffer);
                            }
                            return Flux.empty();
                        }
                    };

                    return chain.filter(exchange.mutate().request(decorator).build());
                })
                .doFinally(onFinally);
    }

    /**
     * 判断是否应该记录请求体
     */
    private boolean shouldLogRequestBody(ServerHttpRequest request) {
        // 只记录POST, PUT, PATCH请求的请求体
        String method = request.getMethod().name();
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }

    /**
     * 获取客户端IP
     */

    @Override
    public int getOrder() {
        return GlobalFilterOrderConfig.REQUEST_LOGGER_FILTER_ORDER; // 使用统一的全局过滤器顺序管理
    }
}