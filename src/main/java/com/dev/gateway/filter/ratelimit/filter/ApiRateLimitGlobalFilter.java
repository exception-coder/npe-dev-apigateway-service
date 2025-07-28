package com.dev.gateway.filter.ratelimit.filter;

import com.dev.gateway.filter.access.context.AccessRecordContextKeys;
import com.dev.gateway.filter.GlobalFilterOrderConfig;
import com.dev.gateway.configuration.RateLimiterConfig;
import com.dev.gateway.properties.GatewayProperties;
import com.dev.gateway.filter.ratelimit.service.RateLimitLogService;
import com.dev.gateway.filter.ratelimit.service.RateLimitService;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.toolkit.trace.TraceContext;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.net.URI;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * API网关全局过滤器
 * 主要功能：
 * 1. 客户端IP识别和限流
 * 2. 验证码机制触发和管理
 * 3. 白名单管理
 * 4. HTTP安全头设置
 * 5. 统一编排所有全局过滤器的执行顺序
 *
 * 全局过滤器执行顺序统一管理：
 * 所有过滤器的执行顺序都在 {@link GlobalFilterOrderConfig} 中统一定义和管理，
 * 避免在各个过滤器中分散定义，便于维护和调整整体架构。
 *
 * @author 系统
 * @version 1.0
 */
@Component
@Slf4j
public class ApiRateLimitGlobalFilter implements GlobalFilter, Ordered {

    // ================= 常量定义 =================

    /**
     * 滑动窗口IP跟踪器
     */
    public static final RateLimiterConfig.SlidingWindowIpTracker SLIDING_WINDOW_IP_TRACKER = RateLimiterConfig.SLIDING_WINDOW_IP_TRACKER;

    /**
     * MDC键值常量
     */
    private static final String MDC_CLIENT_IP = "clientIp";
    private static final String MDC_URI_PATH = "uriPath";

    /**
     * 媒体类型常量
     */
    private static final String MULTIPART_TYPE = "multipart";
    private static final String APPLICATION_TYPE = "application";

    // ================= 依赖注入 =================

    private final GatewayProperties gatewayProperties;
    private final XForwardedRemoteAddressResolver xForwardedRemoteAddressResolver;
    private final RateLimitService rateLimitService;
    private final RateLimitLogService rateLimitLogService;

    /**
     * MDC清理回调
     */
    private final Consumer<SignalType> mdcCleanupCallback = signalType -> cleanupMDC();

    // ================= 构造函数 =================

    public ApiRateLimitGlobalFilter(
            GatewayProperties gatewayProperties,
            XForwardedRemoteAddressResolver xForwardedRemoteAddressResolver,
            RateLimitService rateLimitService,
            RateLimitLogService rateLimitLogService) {
        this.gatewayProperties = gatewayProperties;
        this.xForwardedRemoteAddressResolver = xForwardedRemoteAddressResolver;
        this.rateLimitService = rateLimitService;
        this.rateLimitLogService = rateLimitLogService;
    }

    // ================= 核心过滤逻辑 =================

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.info("链路信息 - traceId: {}, spanId: {}", TraceContext.traceId(), TraceContext.spanId());
        try {
            // 1. 初始化请求上下文
            initializeRequestContext(exchange);

            // 2. 设置安全响应头
            if (gatewayProperties.getSecurity().isEnableSecurityHeaders()) {
                setHttpSecurityHeaders(exchange.getResponse());
            }

            // 3. 检查是否需要限流验证
            if (!gatewayProperties.getRateLimit().isEnabled()) {
                log.info("限流机制未启用，直接放行");
                return chain.filter(exchange).doFinally(mdcCleanupCallback);
            }

            // 4. 处理验证码页面访问
            if (isCaptchaPageRequest(exchange.getRequest())) {
                log.info("访问验证码页面，直接放行");
                return chain.filter(exchange).doFinally(mdcCleanupCallback);
            }

            // 5. 执行限流和验证码逻辑
            return processRateLimitingAndCaptcha(exchange, chain);

        } catch (Exception e) {
            log.error("网关过滤器处理异常: {}", e.getMessage(), e);
            return handleError(exchange, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 初始化请求上下文
     */
    private void initializeRequestContext(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String traceId = TraceContext.traceId();

        MDC.put("traceId", traceId);
        MDC.put("spanId", String.valueOf(TraceContext.spanId()));

        // 获取客户端IP
        String hostAddress = xForwardedRemoteAddressResolver.resolve(exchange)
                .getAddress().getHostAddress();

        // 设置MDC上下文
        MDC.put(MDC_CLIENT_IP, hostAddress);
        MDC.put(MDC_URI_PATH, request.getURI().getPath());

        log.info("请求初始化完成 - IP: {}, 路径: {}, 主机地址: {}",
                hostAddress, request.getURI().getPath(), hostAddress);
    }

    /**
     * 检查是否为验证码页面请求
     */
    private boolean isCaptchaPageRequest(ServerHttpRequest request) {
        String captchaPagePath = gatewayProperties.getCaptcha().getPagePath();
        return captchaPagePath.equals(request.getURI().getPath());
    }

    /**
     * 处理限流和验证码逻辑
     */
    private Mono<Void> processRateLimitingAndCaptcha(ServerWebExchange exchange, GatewayFilterChain chain) {
        String clientIp = MDC.get(MDC_CLIENT_IP);
        String requestPath = exchange.getRequest().getURI().getPath();

        // 快速检查：如果是API请求且配置允许，直接放行
        // if (isApiRequest(requestPath) &&
        // gatewayProperties.getCaptcha().isAllowApiWhenCaptchaActive()) {
        // log.info("API请求且配置允许，直接转发 - IP: {}, Path: {}", clientIp, requestPath);
        // return proceedWithRequest(exchange, chain);
        // }

        // 检查验证码验证标识
        String captchaVerified = exchange.getRequest().getHeaders().getFirst("X-Captcha-Verified");
        if ("true".equals(captchaVerified)) {
            log.info("请求已通过验证码验证，直接转发 - IP: {}", clientIp);
            return proceedWithRequest(exchange, chain);
        }

        // 尝试快速Redis检查，但设置极短超时和快速失败
        return performQuickRedisCheck(exchange, chain, clientIp, requestPath);
    }

    /**
     * 执行快速Redis检查（超时后立即放行）
     */
    private Mono<Void> performQuickRedisCheck(ServerWebExchange exchange, GatewayFilterChain chain, String clientIp,
            String requestPath) {
        // 创建一个极短超时的Redis检查
        Mono<String> quickWhiteListCheck = Mono.fromCallable(() -> {
            // 这里我们不直接调用RateLimitService，而是创建一个快速检查
            return "";
        })
                .timeout(Duration.ofMillis(500)) // 500毫秒极短超时
                .onErrorReturn("REDIS_ERROR"); // 明确标识Redis错误

        return quickWhiteListCheck
                .flatMap(result -> {
                    if ("REDIS_ERROR".equals(result)) {
                        log.warn("Redis快速检查失败，直接转发请求 - IP: {}", clientIp);
                        return proceedWithRequest(exchange, chain);
                    }

                    // Redis可用，尝试实际检查（但仍然有快速失败机制）
                    return performActualRedisCheck(exchange, chain, clientIp, requestPath);
                })
                .onErrorResume(throwable -> {
                    log.warn("Redis检查完全失败，确保请求转发 - IP: {}, 错误: {}", clientIp, throwable.getMessage());
                    return proceedWithRequest(exchange, chain);
                });
    }

    /**
     * 执行实际的Redis检查（带快速失败保护）
     */
    private Mono<Void> performActualRedisCheck(ServerWebExchange exchange, GatewayFilterChain chain, String clientIp,
            String requestPath) {
        // 白名单检查 - 但不依赖onErrorReturn的行为
        return checkWhiteListWithTimeout(clientIp, exchange)
                .defaultIfEmpty(false) // 👈 确保 map 有值可用，避免 Mono.empty()
                .flatMap(isInWhiteList -> {
                    if (isInWhiteList) {
                        log.info("IP在白名单中，直接放行 - IP: {}", clientIp);
                        return proceedWithRequest(exchange, chain);
                    }

                    // 不在白名单，检查验证码机制但有快速失败
                    return checkCaptchaMechanismWithTimeout(exchange, chain, clientIp, requestPath);
                })
                .onErrorResume(throwable -> {
                    log.warn("Redis实际检查失败，保证请求转发 - IP: {}, 错误: {}", clientIp, throwable.getMessage());
                    return proceedWithRequest(exchange, chain);
                });
    }

    /**
     * 带超时的白名单检查并设置Exchange属性
     */
    private Mono<Boolean> checkWhiteListWithTimeout(String clientIp, ServerWebExchange exchange) {
        return rateLimitService.getWhiteList(clientIp)
                .timeout(Duration.ofMillis(800))
                .map(whiteListIp -> {
                    boolean isInWhiteList = clientIp.equals(whiteListIp) && !whiteListIp.isEmpty();
                    // 设置白名单属性到Exchange
                    exchange.getAttributes().put(AccessRecordContextKeys.WHITELIST_FLATMAP, isInWhiteList);
                    return isInWhiteList;
                })
                .onErrorReturn(false);
    }

    /**
     * 带超时的验证码机制检查
     */
    private Mono<Void> checkCaptchaMechanismWithTimeout(ServerWebExchange exchange, GatewayFilterChain chain,
            String clientIp, String requestPath) {
        return rateLimitService.getCaptchaRequired()
                .timeout(Duration.ofMillis(800))
                .flatMap(captchaRequired -> {
                    if (Boolean.TRUE.equals(captchaRequired)) {
                        return handleCaptchaActiveWithFallback(exchange, chain, clientIp, requestPath);
                    } else {
                        // 验证码未激活，检查是否需要激活（但有快速失败）
                        return checkTriggerWithFallback(exchange, chain, clientIp, requestPath);
                    }
                })
                .onErrorResume(throwable -> {
                    log.warn("验证码机制检查超时，默认放行 - IP: {}, 错误: {}", clientIp, throwable.getMessage());
                    return proceedWithRequest(exchange, chain);
                });
    }

    /**
     * 处理验证码激活状态（带降级）
     */
    private Mono<Void> handleCaptchaActiveWithFallback(ServerWebExchange exchange, GatewayFilterChain chain,
            String clientIp, String requestPath) {
        // 非严格模式直接放行
        if (!gatewayProperties.getCaptcha().isStrictMode()) {
            log.info("非严格验证码模式，允许请求转发 - IP: {}", clientIp);
            return proceedWithRequest(exchange, chain);
        }

        // 严格模式下的处理
        if (isApiRequest(requestPath)) {
            return handleApiCaptchaRequired(exchange);
        } else {
            return redirectToCaptcha(exchange);
        }
    }

    /**
     * 检查是否需要触发验证码（带降级）
     */
    private Mono<Void> checkTriggerWithFallback(ServerWebExchange exchange, GatewayFilterChain chain, String clientIp,
            String requestPath) {
        return rateLimitService.getLast10SecondsReqIpCount()
                .timeout(Duration.ofMillis(800))
                .flatMap(ipCount -> {
                    if (ipCount >= gatewayProperties.getCaptcha().getTriggerIpThreshold()) {
                        log.warn("IP数量超过阈值 - 当前: {}, 阈值: {}", ipCount,
                                gatewayProperties.getCaptcha().getTriggerIpThreshold());

                        // 记录验证码触发日志到MongoDB
                        rateLimitLogService.recordCaptchaLog(exchange, clientIp,
                                String.format("IP数量超过阈值，当前:%d，阈值:%d", ipCount,
                                        gatewayProperties.getCaptcha().getTriggerIpThreshold()),
                                ipCount.intValue());

                        // 根据严格模式和请求类型决定
                        if (!gatewayProperties.getCaptcha().isStrictMode()) {
                            return proceedWithRequest(exchange, chain);
                        } else if (isApiRequest(requestPath)) {
                            return handleApiCaptchaRequired(exchange);
                        } else {
                            return redirectToCaptcha(exchange);
                        }
                    } else {
                        // 正常情况
                        return proceedWithRequest(exchange, chain);
                    }
                })
                .onErrorResume(throwable -> {
                    log.warn("IP统计检查超时，默认放行 - IP: {}, 错误: {}", clientIp, throwable.getMessage());
                    return proceedWithRequest(exchange, chain);
                });
    }

    /**
     * 判断是否为API请求
     */
    private boolean isApiRequest(String path) {
        return path != null && (path.startsWith("/pure-admin-service/"));
    }

    /**
     * 处理API请求的验证码要求
     */
    private Mono<Void> handleApiCaptchaRequired(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        // 设置限流属性到Exchange
        exchange.getAttributes().put(AccessRecordContextKeys.RATE_LIMITED, true);
        exchange.getAttributes().put(AccessRecordContextKeys.RATE_LIMIT_TYPE, "CAPTCHA_REQUIRED");

        // 记录限流日志到MongoDB
        String clientIp = MDC.get(MDC_CLIENT_IP);
        rateLimitLogService.recordCaptchaLog(exchange, clientIp, "API请求需要验证码验证", null);

        String errorResponse = "{\"code\":429,\"message\":\"需要验证码验证\",\"data\":null}";
        DataBuffer buffer = response.bufferFactory().wrap(errorResponse.getBytes());

        return response.writeWith(Mono.just(buffer)).doFinally(mdcCleanupCallback);
    }

    /**
     * 重定向到验证码页面
     */
    private Mono<Void> redirectToCaptcha(ServerWebExchange exchange) {
        String captchaPagePath = gatewayProperties.getCaptcha().getPagePath();
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(captchaPagePath));
        return response.setComplete().doFinally(mdcCleanupCallback);
    }

    /**
     * 继续请求处理
     */
    private Mono<Void> proceedWithRequest(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 在请求转发前，登记IP到滑动窗口
        String clientIp = MDC.get(MDC_CLIENT_IP);
        String requestPath = exchange.getRequest().getURI().getPath();

        if (clientIp != null && requestPath != null) {
            // 使用响应式方式检查IP+路径组合是否超过限流阈值
            return SLIDING_WINDOW_IP_TRACKER.isRequestExceedLimitReactive(clientIp, requestPath)
                    .flatMap(isExceedLimit -> {
                        if (isExceedLimit) {
                            log.warn("检测到IP+路径限流触发 - IP: {}, 路径: {}", clientIp, requestPath);
                            // 设置限流属性到Exchange
                            exchange.getAttributes().put(AccessRecordContextKeys.RATE_LIMITED, true);
                            exchange.getAttributes().put(AccessRecordContextKeys.RATE_LIMIT_TYPE,
                                    "SLIDING_WINDOW_IP_PATH");
                            // 如果超过限流，返回429错误
                            return handleRateLimitExceeded(exchange);
                        } else {
                            log.debug("IP+路径访问正常，已登记到滑动窗口 - IP: {}, 路径: {}", clientIp, requestPath);
                            // 设置未限流属性到Exchange
                            exchange.getAttributes().put(AccessRecordContextKeys.RATE_LIMITED, false);
                            // 继续处理请求
                            return chain.filter(exchange);
                        }
                    })
                    .onErrorResume(throwable -> {
                        log.error("滑动窗口IP+路径检查失败 - IP: {}, 路径: {}, 错误: {}", clientIp, requestPath,
                                throwable.getMessage(), throwable);
                        // 发生错误时继续处理请求，避免因限流组件异常影响正常请求
                        exchange.getAttributes().put(AccessRecordContextKeys.RATE_LIMITED, false);
                        return chain.filter(exchange);
                    })
                    .doFinally(mdcCleanupCallback);
        } else {
            // 如果IP或路径为空，直接继续处理
            exchange.getAttributes().put(AccessRecordContextKeys.RATE_LIMITED, false);
            return chain.filter(exchange).doFinally(mdcCleanupCallback);
        }
    }

    /**
     * 处理限流超出时的响应
     */
    private Mono<Void> handleRateLimitExceeded(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        // 记录滑动窗口限流日志到MongoDB
        String clientIp = MDC.get(MDC_CLIENT_IP);
        String requestPath = exchange.getRequest().getURI().getPath();
        rateLimitLogService.recordRateLimitLog(exchange, clientIp, "SLIDING_WINDOW_IP_PATH",
                "IP+路径滑动窗口限流触发", null, null, null);

        String errorResponse = "{\"code\":429,\"message\":\"请求频率过高，请稍后再试\",\"data\":null}";
        DataBuffer buffer = response.bufferFactory().wrap(errorResponse.getBytes());

        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 处理错误响应
     */
    private Mono<Void> handleError(ServerWebExchange exchange, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        return response.setComplete().doFinally(mdcCleanupCallback);
    }

    /**
     * 清理MDC上下文
     */
    private void cleanupMDC() {
        MDC.remove(MDC_CLIENT_IP);
        MDC.remove(MDC_URI_PATH);
    }

    // ================= 工具方法 =================

    // ================= HTTP安全配置 =================

    /**
     * 为Cookie添加HttpOnly标志
     */
    private String addHttpOnlyFlag(String cookie) {
        if (!cookie.toLowerCase().contains("httponly")) {
            return cookie + "; HttpOnly";
        }
        return cookie;
    }

    /**
     * 设置HTTP安全响应头
     */
    private void setHttpSecurityHeaders(ServerHttpResponse response) {
        HttpHeaders headers = response.getHeaders();
        GatewayProperties.SecurityConfig securityConfig = gatewayProperties.getSecurity();

        // 防止信息泄露
        headers.add("Referrer-Policy", securityConfig.getReferrerPolicy());

        // 内容安全策略
        headers.add("Content-Security-Policy", securityConfig.getContentSecurityPolicy());

        // 防止点击劫持
        headers.add("X-Frame-Options", securityConfig.getFrameOptions());

        // 防止MIME类型嗅探
        headers.add("X-Content-Type-Options", "nosniff");

        // XSS保护
        headers.add("X-XSS-Protection", "1; mode=block");

        log.debug("HTTP安全头设置完成");
    }

    // ================= 过滤器配置 =================

    /**
     * 设置过滤器优先级
     * 使用统一的全局过滤器顺序管理
     *
     * @return 优先级值，越小优先级越高
     */
    @Override
    public int getOrder() {
        return GlobalFilterOrderConfig.API_RATE_LIMIT_FILTER_ORDER;
    }
}
