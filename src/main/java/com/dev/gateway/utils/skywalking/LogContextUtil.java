package com.dev.gateway.utils.skywalking;

import org.apache.skywalking.apm.toolkit.trace.TraceContext;
import org.slf4j.MDC;
import org.springframework.web.server.ServerWebExchange;

public class LogContextUtil {
    public static void initSkywalkingTraceContext() {
        try {
            MDC.put("traceId", TraceContext.traceId());
            MDC.put("spanId", String.valueOf(TraceContext.spanId()));
        } catch (Exception ignored) {
        }
    }

    public static void initSkywalkingTraceContext(ServerWebExchange exchange,String traceId,int spanId) {
        try {
            exchange.getAttributes().put("traceId", traceId);
            exchange.getAttributes().put("spanId", spanId);
            MDC.put("traceId", traceId);
            MDC.put("spanId", String.valueOf(spanId));
        } catch (Exception ignored) {
        }
    }
}
