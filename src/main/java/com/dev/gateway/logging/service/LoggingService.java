package com.dev.gateway.logging.service;

import com.dev.gateway.logging.properties.LoggingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 日志服务类
 * 封装所有日志相关的操作
 */
@Service
@Slf4j
public class LoggingService {

    @Autowired
    private LoggingProperties loggingProperties;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * 判断是否应该跳过日志记录
     */
    public boolean shouldSkipLogging(String requestUri) {
        return Arrays.stream(loggingProperties.getSkipPaths())
                .anyMatch(pattern -> requestUri.matches(pattern.replace("**", ".*")));
    }

    /**
     * 记录请求日志
     */
    public void logRequest(ServerWebExchange exchange, String requestBody) {
        if (!loggingProperties.isRequestBodyEnabled()) {
            return;
        }

        ServerHttpRequest request = exchange.getRequest();
        String requestUri = request.getURI().getPath();

        if (shouldSkipLogging(requestUri)) {
            return;
        }

        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append("\n=== 请求日志 ===\n");
        logBuilder.append("时间: ").append(LocalDateTime.now().format(FORMATTER)).append("\n");
        logBuilder.append("方法: ").append(request.getMethod()).append("\n");
        logBuilder.append("URI: ").append(request.getURI()).append("\n");
        logBuilder.append("远程地址: ").append(getClientIp(request)).append("\n");

        // 记录请求头
        if (loggingProperties.isLogHeaders()) {
            logBuilder.append("请求头:\n");
            request.getHeaders().forEach((name, values) -> {
                if (!isSensitiveHeader(name)) {
                    logBuilder.append("  ").append(name).append(": ").append(String.join(", ", values)).append("\n");
                } else {
                    logBuilder.append("  ").append(name).append(": [已隐藏]\n");
                }
            });
        }

        // 记录请求体
        if (StringUtils.hasText(requestBody)) {
            String filteredBody = filterSensitiveInfo(requestBody);
            String truncatedBody = truncateText(filteredBody, loggingProperties.getMaxRequestBodyLength());
            logBuilder.append("请求体: ").append(truncatedBody).append("\n");
        }

        logBuilder.append("================\n");

        if (loggingProperties.isVerboseLogging()) {
            log.info(logBuilder.toString());
        } else {
            log.debug(logBuilder.toString());
        }
    }

    /**
     * 记录响应日志
     */
    public void logResponse(ServerWebExchange exchange, String responseBody) {
        if (!loggingProperties.isResponseBodyEnabled()) {
            return;
        }

        ServerHttpRequest request = exchange.getRequest();
        String requestUri = request.getURI().getPath();

        if (shouldSkipLogging(requestUri)) {
            return;
        }

        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append("\n=== 响应日志 ===\n");
        logBuilder.append("时间: ").append(LocalDateTime.now().format(FORMATTER)).append("\n");
        logBuilder.append("URI: ").append(request.getURI()).append("\n");
        logBuilder.append("状态码: ").append(exchange.getResponse().getStatusCode()).append("\n");

        // 记录响应头
        if (loggingProperties.isLogResponseHeaders()) {
            logBuilder.append("响应头:\n");
            exchange.getResponse().getHeaders().forEach((name, values) -> {
                logBuilder.append("  ").append(name).append(": ").append(String.join(", ", values)).append("\n");
            });
        }

        // 记录响应体
        if (StringUtils.hasText(responseBody)) {
            String filteredBody = filterSensitiveInfo(responseBody);
            String truncatedBody = truncateText(filteredBody, loggingProperties.getMaxResponseBodyLength());
            logBuilder.append("响应体: ").append(truncatedBody).append("\n");
        }

        logBuilder.append("================\n");

        if (loggingProperties.isVerboseLogging()) {
            log.info(logBuilder.toString());
        } else {
            log.debug(logBuilder.toString());
        }
    }

    /**
     * 记录访问日志
     */
    public void logAccess(ServerWebExchange exchange, long duration) {
        if (!loggingProperties.isAccessLogEnabled()) {
            return;
        }

        ServerHttpRequest request = exchange.getRequest();
        String requestUri = request.getURI().getPath();

        if (shouldSkipLogging(requestUri)) {
            return;
        }

        String clientIp = getClientIp(request);
        String method = request.getMethod().toString();
        String uri = request.getURI().toString();
        String userAgent = request.getHeaders().getFirst(HttpHeaders.USER_AGENT);
        Integer statusCode = exchange.getResponse().getStatusCode() != null ? 
                exchange.getResponse().getStatusCode().value() : 0;

        log.info("ACCESS - {} {} {} - {} - {}ms - Agent: {}", 
                clientIp, method, uri, statusCode, duration, userAgent);
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (StringUtils.hasText(xRealIp)) {
            return xRealIp;
        }

        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }

        return "unknown";
    }

    /**
     * 判断是否为敏感请求头
     */
    private boolean isSensitiveHeader(String headerName) {
        String lowerCaseName = headerName.toLowerCase();
        return Arrays.stream(loggingProperties.getSensitiveFields())
                .anyMatch(sensitiveField -> lowerCaseName.contains(sensitiveField.toLowerCase()));
    }

    /**
     * 过滤敏感信息
     */
    private String filterSensitiveInfo(String content) {
        if (!StringUtils.hasText(content)) {
            return content;
        }

        String result = content;
        for (String sensitiveField : loggingProperties.getSensitiveFields()) {
            // 简单的敏感信息过滤，实际项目中可能需要更复杂的正则表达式
            String pattern = "\"" + sensitiveField + "\"\\s*:\\s*\"[^\"]*\"";
            result = result.replaceAll("(?i)" + pattern, "\"" + sensitiveField + "\":\"[已隐藏]\"");
        }
        return result;
    }

    /**
     * 截断文本
     */
    private String truncateText(String text, int maxLength) {
        if (!StringUtils.hasText(text) || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "... [截断]";
    }

    /**
     * 获取日志统计信息
     */
    public LoggingStats getStats() {
        LoggingStats stats = new LoggingStats();
        stats.setAccessLogEnabled(loggingProperties.isAccessLogEnabled());
        stats.setRequestBodyEnabled(loggingProperties.isRequestBodyEnabled());
        stats.setResponseBodyEnabled(loggingProperties.isResponseBodyEnabled());
        stats.setVerboseLogging(loggingProperties.isVerboseLogging());
        return stats;
    }

    /**
     * 日志统计信息类
     */
    public static class LoggingStats {
        private boolean accessLogEnabled;
        private boolean requestBodyEnabled;
        private boolean responseBodyEnabled;
        private boolean verboseLogging;

        // getters and setters
        public boolean isAccessLogEnabled() { return accessLogEnabled; }
        public void setAccessLogEnabled(boolean accessLogEnabled) { this.accessLogEnabled = accessLogEnabled; }

        public boolean isRequestBodyEnabled() { return requestBodyEnabled; }
        public void setRequestBodyEnabled(boolean requestBodyEnabled) { this.requestBodyEnabled = requestBodyEnabled; }

        public boolean isResponseBodyEnabled() { return responseBodyEnabled; }
        public void setResponseBodyEnabled(boolean responseBodyEnabled) { this.responseBodyEnabled = responseBodyEnabled; }

        public boolean isVerboseLogging() { return verboseLogging; }
        public void setVerboseLogging(boolean verboseLogging) { this.verboseLogging = verboseLogging; }
    }
} 