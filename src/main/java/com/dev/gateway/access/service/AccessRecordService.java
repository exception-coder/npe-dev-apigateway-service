package com.dev.gateway.access.service;

import com.alibaba.fastjson.JSONObject;
import com.dev.gateway.access.context.AccessRecordContextKeys;
import com.dev.gateway.access.model.AccessRecord;
import com.dev.gateway.access.repository.AccessRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.toolkit.trace.TraceContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 访问记录服务类
 * 处理访问记录的业务逻辑
 */
@Service
@Slf4j
public class AccessRecordService {

    private final AccessRecordRepository accessRecordRepository;

    public AccessRecordService(AccessRecordRepository accessRecordRepository) {
        this.accessRecordRepository = accessRecordRepository;
    }

    /**
     * 构建访问记录
     */
    public AccessRecord buildAccessRecord(ServerWebExchange exchange, String recordId) {
        ServerHttpRequest request = exchange.getRequest();

        AccessRecord.AccessRecordBuilder builder = AccessRecord.builder()
                .id(recordId)
                .clientIp(getClientIp(exchange))
                .requestPath(request.getURI().getPath())
                .httpMethod(request.getMethod().name())
                .userAgent(request.getHeaders().getFirst(HttpHeaders.USER_AGENT))
                .requestHeaders(getRequestHeadersAsJson(request))
                .accessTime(LocalDateTime.now())
                .rateLimited(false)
                .inWhiteList(false);

        // 获取链路追踪信息（使用SkyWalking API）
        String traceId = getCurrentTraceId();
        String spanId = getCurrentSpanId();
        builder.traceId(traceId).spanId(spanId);

        return builder.build();
    }


    public void updateAccessRecordOnComplete(ServerWebExchange exchange) {
        long startTime = (long) exchange.getAttributes().get(AccessRecordContextKeys.REQUEST_START_TIME);

        AccessRecord accessRecord = (AccessRecord) exchange.getAttributes().get(AccessRecordContextKeys.ACCESS);
        updateAccessRecordOnComplete(exchange, accessRecord, startTime);
    }

    /**
     * 请求完成时更新访问记录
     */
    public void updateAccessRecordOnComplete(ServerWebExchange exchange, AccessRecord accessRecord, long startTime) {
        try {
            ServerHttpResponse response = exchange.getResponse();

            // 计算处理时间
            long processingTime = System.currentTimeMillis() - startTime;
            accessRecord.setProcessingTimeMs(processingTime);

            // 设置响应状态码
            if (response.getStatusCode() != null) {
                accessRecord.setResponseStatus(response.getStatusCode().value());
            }

            // 从Exchange属性中获取限流和白名单信息
            Boolean rateLimited = exchange.getAttribute(AccessRecordContextKeys.RATE_LIMITED);
            if (rateLimited != null) {
                accessRecord.setRateLimited(rateLimited);
                if (rateLimited) {
                    String rateLimitType = exchange.getAttribute(AccessRecordContextKeys.RATE_LIMIT_TYPE);
                    accessRecord.setRateLimitType(rateLimitType);
                }
            }

            Boolean inWhiteList = exchange.getAttribute(AccessRecordContextKeys.WHITELIST_FLATMAP);
            if (inWhiteList != null) {
                accessRecord.setInWhiteList(inWhiteList);
            }

            String responseBody = exchange.getAttributes().get(AccessRecordContextKeys.RESPONSE_BODY).toString();
            accessRecord.setRequestBody(responseBody);

            // 异步更新记录
            recordAccessAsync(accessRecord);

        } catch (Exception e) {
            log.error("更新访问记录失败 - ID: {}, 错误: {}", accessRecord.getId(), e.getMessage());
        }
    }

    /**
     * 记录访问日志（异步）
     *
     * @param accessRecord 访问记录
     * @return 保存结果
     */
    public Mono<AccessRecord> recordAccess(AccessRecord accessRecord) {
        // 设置默认值
        if (!StringUtils.hasText(accessRecord.getId())) {
            accessRecord.setId(UUID.randomUUID().toString());
        }
        if (accessRecord.getAccessTime() == null) {
            accessRecord.setAccessTime(LocalDateTime.now());
        }
        if (accessRecord.getRateLimited() == null) {
            accessRecord.setRateLimited(false);
        }
        if (accessRecord.getInWhiteList() == null) {
            accessRecord.setInWhiteList(false);
        }

        return accessRecordRepository.save(accessRecord)
                .doOnSuccess(record -> {
                    if (log.isDebugEnabled()) {
                        log.debug("访问记录已保存 - IP: {}, 路径: {}, ID: {}",
                                record.getClientIp(), record.getRequestPath(), record.getId());
                    }
                })
                .doOnError(error -> {
                    log.error("保存访问记录失败 - IP: {}, 路径: {}, 错误: {}",
                            accessRecord.getClientIp(), accessRecord.getRequestPath(), error.getMessage());
                })
                .onErrorResume(error -> Mono.empty()); // 异常时返回空，不影响主流程
    }

    /**
     * 异步记录访问日志（火忘模式）
     *
     * @param accessRecord 访问记录
     */
    public void recordAccessAsync(AccessRecord accessRecord) {
        recordAccess(accessRecord)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    /**
     * 根据IP查询访问记录
     *
     * @param clientIp 客户端IP
     * @param page     页码（从0开始）
     * @param size     每页大小
     * @return 访问记录流
     */
    public Flux<AccessRecord> getAccessRecordsByIp(String clientIp, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return accessRecordRepository.findByClientIpOrderByAccessTimeDesc(clientIp, pageable)
                .doOnError(error -> log.error("查询IP访问记录失败 - IP: {}, 错误: {}", clientIp, error.getMessage()));
    }

    /**
     * 查询指定时间范围内的访问记录
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param page      页码
     * @param size      每页大小
     * @return 访问记录流
     */
    public Flux<AccessRecord> getAccessRecordsByTimeRange(LocalDateTime startTime, LocalDateTime endTime,
                                                          int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return accessRecordRepository.findByAccessTimeBetweenOrderByAccessTimeDesc(startTime, endTime, pageable)
                .doOnError(error -> log.error("查询时间范围访问记录失败 - 错误: {}", error.getMessage()));
    }

    /**
     * 查询被限流的访问记录
     *
     * @param page 页码
     * @param size 每页大小
     * @return 访问记录流
     */
    public Flux<AccessRecord> getRateLimitedRecords(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return accessRecordRepository.findByRateLimitedOrderByAccessTimeDesc(true, pageable)
                .doOnError(error -> log.error("查询限流访问记录失败 - 错误: {}", error.getMessage()));
    }

    /**
     * 统计指定IP在时间范围内的访问次数
     *
     * @param clientIp  客户端IP
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 访问次数
     */
    public Mono<Long> countAccessByIpAndTimeRange(String clientIp, LocalDateTime startTime, LocalDateTime endTime) {
        return accessRecordRepository.countByClientIpAndAccessTimeBetween(clientIp, startTime, endTime)
                .doOnError(error -> log.error("统计IP访问次数失败 - IP: {}, 错误: {}", clientIp, error.getMessage()))
                .onErrorReturn(0L);
    }

    /**
     * 统计指定时间范围内被限流的访问次数
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 被限流的访问次数
     */
    public Mono<Long> countRateLimitedAccess(LocalDateTime startTime, LocalDateTime endTime) {
        return accessRecordRepository.countRateLimitedBetween(startTime, endTime)
                .doOnError(error -> log.error("统计限流访问次数失败 - 错误: {}", error.getMessage()))
                .onErrorReturn(0L);
    }

    /**
     * 获取访问统计信息
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 统计信息
     */
    public Mono<AccessStatistics> getAccessStatistics(LocalDateTime startTime, LocalDateTime endTime) {
        return Mono.zip(
                        // 总访问次数
                        accessRecordRepository.countByAccessTimeBetween(startTime, endTime).onErrorReturn(0L),
                        // 被限流的访问次数
                        countRateLimitedAccess(startTime, endTime),
                        // 活跃IP数量
                        accessRecordRepository.countDistinctClientIpByAccessTimeBetween(startTime, endTime).onErrorReturn(0L)
                ).map(tuple -> AccessStatistics.builder()
                        .totalAccess(tuple.getT1())
                        .rateLimitedAccess(tuple.getT2())
                        .uniqueIpCount(tuple.getT3())
                        .startTime(startTime)
                        .endTime(endTime)
                        .build())
                .doOnError(error -> log.error("获取访问统计信息失败 - 错误: {}", error.getMessage()));
    }

    /**
     * 清理过期的访问记录
     *
     * @param beforeTime 清理此时间之前的记录
     * @return 删除的记录数量
     */
    public Mono<Long> cleanupOldRecords(LocalDateTime beforeTime) {
        return accessRecordRepository.deleteByAccessTimeBefore(beforeTime)
                .doOnSuccess(count -> log.info("清理过期访问记录完成，删除了 {} 条记录", count))
                .doOnError(error -> log.error("清理过期访问记录失败 - 错误: {}", error.getMessage()))
                .onErrorReturn(0L);
    }

    /**
     * 构建访问记录Builder
     *
     * @param clientIp    客户端IP
     * @param requestPath 请求路径
     * @return AccessRecord Builder
     */
    public static AccessRecord.AccessRecordBuilder buildAccessRecord(String clientIp, String requestPath) {
        return AccessRecord.builder()
                .id(UUID.randomUUID().toString())
                .clientIp(clientIp)
                .requestPath(requestPath)
                .accessTime(LocalDateTime.now())
                .rateLimited(false)
                .inWhiteList(false);
    }


    /**
     * 使用SkyWalking API获取当前的traceId
     */
    private String getCurrentTraceId() {
        try {
            String traceId = TraceContext.traceId();
            if (traceId != null && !traceId.isEmpty()) {
                return traceId;
            }
        } catch (Exception e) {
            log.warn("获取当前traceId失败: {}", e.getMessage());
        }

        // 如果没有找到，生成一个新的traceId
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 使用SkyWalking API获取当前的spanId
     */
    private String getCurrentSpanId() {
        try {
            String spanId = String.valueOf(TraceContext.spanId());
            if (spanId != null && !spanId.isEmpty() && !spanId.equals("0")) {
                return spanId;
            }
        } catch (Exception e) {
            log.warn("获取当前spanId失败: {}", e.getMessage());
        }

        // 如果没有找到，生成一个新的spanId
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * 获取客户端IP地址
     */
    private String getClientIp(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();

        // 尝试从X-Forwarded-For头获取
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        // 尝试从X-Real-IP头获取
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        // 从远程地址获取
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }

        return "unknown";
    }

    /**
     * 将请求头转换为JSON字符串
     */
    private String getRequestHeadersAsJson(ServerHttpRequest request) {
        try {
            JSONObject headers = new JSONObject();
            request.getHeaders().forEach((key, values) -> {
                if (values.size() == 1) {
                    headers.put(key, values.get(0));
                } else {
                    headers.put(key, values);
                }
            });
            return headers.toJSONString();
        } catch (Exception e) {
            log.warn("转换请求头为JSON失败: {}", e.getMessage());
            return "{}";
        }
    }


    /**
     * 访问统计信息数据类
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AccessStatistics {
        /**
         * 总访问次数
         */
        private Long totalAccess;

        /**
         * 被限流的访问次数
         */
        private Long rateLimitedAccess;

        /**
         * 唯一IP数量
         */
        private Long uniqueIpCount;

        /**
         * 统计开始时间
         */
        private LocalDateTime startTime;

        /**
         * 统计结束时间
         */
        private LocalDateTime endTime;

        /**
         * 限流率
         */
        public Double getRateLimitedRate() {
            if (totalAccess == null || totalAccess == 0) {
                return 0.0;
            }
            return (double) rateLimitedAccess / totalAccess * 100;
        }
    }


}