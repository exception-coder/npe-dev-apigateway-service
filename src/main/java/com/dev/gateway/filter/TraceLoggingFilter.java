package com.dev.gateway.filter;

import com.dev.gateway.service.IpResolverService;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.toolkit.trace.TraceContext;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class TraceLoggingFilter implements GlobalFilter, Ordered {

    private final IpResolverService ipResolverService;

    public TraceLoggingFilter(IpResolverService ipResolverService) {
        this.ipResolverService = ipResolverService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 获取客户端IP和请求路径
        String clientIp = ipResolverService.getClientIp(exchange);
        String uriPath = exchange.getRequest().getURI().getPath();

        return Mono.deferContextual(ctx -> {
            // 在 Reactor 上下文中设置 MDC
            return Mono.fromRunnable(() -> {
                try {
                    // 获取SkyWalking的TraceId和SpanId
                    String traceId = TraceContext.traceId();
                    String spanId = String.valueOf(TraceContext.spanId());

                    // 验证 TraceId 是否有效
                    boolean isValidTrace = traceId != null && !traceId.isEmpty() &&
                            !"N/A".equals(traceId) && !"null".equals(traceId);
                    boolean isValidSpan = spanId != null && !spanId.equals("0") &&
                            !spanId.equals("-1") && !"null".equals(spanId);

                    // 设置MDC上下文
                    MDC.put("clientIp", clientIp != null ? clientIp : "unknown");
                    MDC.put("uriPath", uriPath != null ? uriPath : "unknown");

                    if (isValidSpan) {
                        MDC.put("spanId", spanId);
                    } else {
                        MDC.put("spanId", "-1");
                    }

                    // 日志输出，帮助调试
                    if (isValidTrace && isValidSpan) {
                        log.debug("SkyWalking 链路追踪上下文已设置 - IP: {}, URI: {}, TraceId: {}, SpanId: {}",
                                clientIp, uriPath, traceId, spanId);
                    } else {
                        log.warn("SkyWalking 链路追踪上下文不完整 - IP: {}, URI: {}, TraceId: {}, SpanId: {} " +
                                "(可能 SkyWalking Agent 未正确启动)",
                                clientIp, uriPath, traceId, spanId);
                    }

                } catch (Exception e) {
                    log.warn("设置 SkyWalking 链路追踪上下文失败: {} - IP: {}, URI: {}",
                            e.getMessage(), clientIp, uriPath);
                    // 设置默认值
                    MDC.put("clientIp", clientIp != null ? clientIp : "unknown");
                    MDC.put("uriPath", uriPath != null ? uriPath : "unknown");
                    MDC.put("spanId", "-1");
                }
            }).then(chain.filter(exchange))
                    .doFinally(signalType -> {
                        // 清理MDC上下文，避免内存泄漏
                        MDC.remove("clientIp");
                        MDC.remove("uriPath");
                        MDC.remove("spanId");
                    });
        });
    }

    @Override
    public int getOrder() {
        return GlobalFilterOrderConfig.TRACE_LOGGING_FILTER_ORDER; // 使用统一的全局过滤器顺序管理
    }

}
