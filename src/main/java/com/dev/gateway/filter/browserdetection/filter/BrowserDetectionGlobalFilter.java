package com.dev.gateway.filter.browserdetection.filter;

import com.dev.gateway.filter.browserdetection.properties.BrowserDetectionProperties;
import com.dev.gateway.filter.browserdetection.service.BrowserDetectionService;
import com.dev.gateway.configuration.GlobalFilterOrderConfig;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 浏览器检测全局过滤器
 * 检测并拦截非真实浏览器的请求
 */
@Component
@Slf4j
public class BrowserDetectionGlobalFilter implements GlobalFilter, Ordered {

    private final BrowserDetectionService browserDetectionService;

    private final BrowserDetectionProperties properties;

    private final Consumer<SignalType> onFinally = Objects.requireNonNull(signalType -> {
        MDC.remove("browserDetection");
        MDC.remove("detectionResult");
    });

    public BrowserDetectionGlobalFilter(BrowserDetectionService browserDetectionService, BrowserDetectionProperties properties) {
        this.browserDetectionService = browserDetectionService;
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 检查是否启用浏览器检测
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String requestUri = request.getURI().getPath();
        String clientIp = getClientIp(request);

        // 设置MDC上下文
        MDC.put("browserDetection", "enabled");
        MDC.put("clientIp", clientIp);

        // 检查是否应该跳过检测
        if (browserDetectionService.shouldSkipDetection(requestUri)) {
            if (properties.isVerboseLogging()) {
                log.debug("跳过浏览器检测 - IP: {}, URI: {}", clientIp, requestUri);
            }
            return chain.filter(exchange).doFinally(onFinally);
        }

        if (properties.isVerboseLogging()) {
            log.debug("开始浏览器检测 - IP: {}, URI: {}", clientIp, requestUri);
        }

        // 执行浏览器检测
        try {
            BrowserDetectionService.BrowserDetectionResult detectionResult = 
                    browserDetectionService.detectBrowser(exchange);
            
            MDC.put("detectionResult", String.valueOf(detectionResult.isBrowser()));

            if (detectionResult.isBrowser()) {
                // 检测通过，允许请求继续
                if (properties.isVerboseLogging()) {
                    log.info("浏览器检测通过 - IP: {}, 得分: {}", clientIp, detectionResult.getFinalScore());
                }
                return chain.filter(exchange).doFinally(onFinally);
            } else {
                // 检测失败，拒绝请求
                log.warn("浏览器检测失败，拒绝请求 - IP: {}, URI: {}, 原因: {}", 
                        clientIp, requestUri, detectionResult.getRejectionReason());
                return rejectRequest(exchange, detectionResult.getRejectionReason());
            }
        } catch (Exception e) {
            log.error("浏览器检测过程中发生异常 - IP: {}, URI: {}", clientIp, requestUri, e);
            
            // 异常情况下的处理策略：根据配置决定是放行还是拒绝
            if (properties.getStrictness() == BrowserDetectionProperties.StrictnessLevel.STRICT) {
                // 严格模式下，异常时拒绝请求
                return rejectRequest(exchange, "浏览器检测异常，请求被拒绝");
            } else {
                // 非严格模式下，异常时放行请求
                log.warn("浏览器检测异常，但允许请求通过 - IP: {}", clientIp);
                return chain.filter(exchange).doFinally(onFinally);
            }
        }
    }

    /**
     * 拒绝请求
     */
    private Mono<Void> rejectRequest(ServerWebExchange exchange, String reason) {
        ServerHttpResponse response = exchange.getResponse();
        
        // 设置响应状态和头
        response.setStatusCode(HttpStatus.valueOf(properties.getRejectionStatusCode()));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // 构建响应体
        String responseBody = String.format(
                "{\"success\": false, \"message\": \"%s\", \"code\": %d, \"timestamp\": %d}",
                properties.getRejectionMessage(),
                properties.getRejectionStatusCode(),
                System.currentTimeMillis()
        );

        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        response.getHeaders().setContentLength(bytes.length);

        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)))
                .doFinally(onFinally);
    }

    /**
     * 获取客户端IP地址
     */
    private String getClientIp(ServerHttpRequest request) {
        // 优先从X-Forwarded-For获取
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        // 从X-Real-IP获取
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        // 从Mock-IP获取（测试用）
        String mockIp = request.getHeaders().getFirst("Mock-IP");
        if (mockIp != null && !mockIp.isEmpty()) {
            return mockIp;
        }

        // 最后从连接信息获取
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }

        return "unknown";
    }

    @Override
    public int getOrder() {
        return GlobalFilterOrderConfig.BROWSER_DETECTION_FILTER_ORDER; // 使用统一的全局过滤器顺序管理
    }
} 