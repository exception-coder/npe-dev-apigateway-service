package com.dev.gateway.filter.access.integration;

import com.dev.gateway.filter.access.context.AccessRecordContextKeys;
import com.dev.gateway.filter.access.model.AccessRecord;
import com.dev.gateway.filter.access.service.AccessRecordService;
import com.dev.gateway.configuration.GlobalFilterOrderConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 访问记录过滤器
 * 在请求处理过程中记录访问信息到MongoDB
 */
@Component
@Slf4j
public class AccessRecordingFilter implements GlobalFilter, Ordered {

    private final AccessRecordService accessRecordService;




    public AccessRecordingFilter(AccessRecordService accessRecordService) {
        this.accessRecordService = accessRecordService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 记录请求开始时间
        long startTime = System.currentTimeMillis();
        exchange.getAttributes().put(AccessRecordContextKeys.REQUEST_START_TIME, startTime);

        // 生成访问记录ID
        String recordId = UUID.randomUUID().toString();
        exchange.getAttributes().put(AccessRecordContextKeys.ACCESS_RECORD_ID, recordId);

        // 构建访问记录
        AccessRecord accessRecord = accessRecordService.buildAccessRecord(exchange, recordId);

        // 异步记录访问开始
        accessRecordService.recordAccessAsync(accessRecord);

        return chain.filter(exchange)
                .doFinally(signalType -> {
                    exchange.getAttributes().put(AccessRecordContextKeys.ACCESS, accessRecord);
                });
    }

    /**
     * 限制字符串长度，避免存储过大的内容
     */
    private String limitString(String str, int maxLength) {
        if (str == null) {
            return null;
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...[截断]";
    }

    @Override
    public int getOrder() {
        return GlobalFilterOrderConfig.ACCESS_RECORDING_FILTER_ORDER; // 使用统一的全局过滤器顺序管理
    }
}