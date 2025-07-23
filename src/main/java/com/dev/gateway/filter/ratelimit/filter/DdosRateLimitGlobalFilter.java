package com.dev.gateway.filter.ratelimit.filter;

import com.dev.gateway.filter.access.context.AccessRecordContextKeys;
import com.dev.gateway.common.constants.HttpHeaderConstants;
import com.dev.gateway.configuration.GlobalFilterOrderConfig;
import com.dev.gateway.filter.ratelimit.properties.RateLimitProperties;
import com.dev.gateway.filter.ratelimit.service.RateLimitLogService;
import com.dev.gateway.filter.ratelimit.service.RateLimitService;
import com.dev.gateway.service.IpResolverService;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.toolkit.trace.TraceContext;
import org.apache.skywalking.apm.toolkit.webflux.WebFluxSkyWalkingTraceContext;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 基于Redis的网关全局限流拦截器
 * 主要功能：
 * 1. IP限流防护
 * 2. DDoS攻击防护
 * 3. 滑动窗口计数器
 * 4. 动态验证码机制
 */
@Component
@Slf4j
public class DdosRateLimitGlobalFilter implements GlobalFilter, Ordered {

    private final RateLimitService rateLimitService;

    private final RateLimitProperties rateLimitProperties;

    private final IpResolverService ipResolverService;

    private final RateLimitLogService rateLimitLogService;

    private final Consumer<SignalType> onFinally = Objects.requireNonNull(signalType -> {
        MDC.remove("clientIp");
        MDC.remove("uriPath");
    });

    public DdosRateLimitGlobalFilter(RateLimitService rateLimitService, RateLimitProperties rateLimitProperties,
            IpResolverService ipResolverService, RateLimitLogService rateLimitLogService) {
        this.rateLimitService = rateLimitService;
        this.rateLimitProperties = rateLimitProperties;
        this.ipResolverService = ipResolverService;
        this.rateLimitLogService = rateLimitLogService;
    }

    /**
     * 限流检查结果内部类
     */
    private static class RateLimitCheckResult {
        private final boolean allowed;
        private final String limitType;
        private final Integer currentCount;
        private final Integer threshold;
        private final Integer windowSize;

        public RateLimitCheckResult(boolean allowed, String limitType, Integer currentCount, Integer threshold,
                Integer windowSize) {
            this.allowed = allowed;
            this.limitType = limitType;
            this.currentCount = currentCount;
            this.threshold = threshold;
            this.windowSize = windowSize;
        }

        public boolean isAllowed() {
            return allowed;
        }

        public String getLimitType() {
            return limitType;
        }

        public Integer getCurrentCount() {
            return currentCount;
        }

        public Integer getThreshold() {
            return threshold;
        }

        public Integer getWindowSize() {
            return windowSize;
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.info("链路信息 - traceId: {}, spanId: {}", TraceContext.traceId(), TraceContext.spanId());
        WebFluxSkyWalkingTraceContext.putCorrelation(exchange, "traceId", TraceContext.traceId());
        exchange.getResponse().getHeaders().set(HttpHeaderConstants.X_TRACE_ID, TraceContext.traceId());
        // 必须在这个 deferContextual 中，才是 SkyWalking 可感知的 Reactor 上下文
        return Mono.deferContextual(ctx -> {
            log.info("WebFlux链路信息 - traceId: {}, spanId: {}",
                    WebFluxSkyWalkingTraceContext.getCorrelation(exchange, "traceId"),
                    WebFluxSkyWalkingTraceContext.getCorrelation(exchange, "spanId"));
            log.info("链路信息 - traceId: {}, spanId: {}", TraceContext.traceId(), TraceContext.spanId());
            // 检查是否启用限流
            if (!rateLimitProperties.isEnabled()) {
                return chain.filter(exchange);
            }

            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();

            // 获取客户端IP和请求URI
            String clientIp = ipResolverService.getClientIp(exchange);
            String requestUri = request.getURI().getPath();

            // 设置MDC日志上下文
            MDC.put("clientIp", clientIp);
            MDC.put("uriPath", requestUri);

            // 设置安全响应头
            setHttpSecurityHeaders(response);

            if (rateLimitProperties.isVerboseLogging()) {
                log.info("请求开始处理 - IP: {}, URI: {}", clientIp, requestUri);
            }

            // 检查是否为跳过限流的路径
            if (shouldSkipRateLimit(requestUri)) {
                if (rateLimitProperties.isVerboseLogging()) {
                    log.info("跳过限流检查的路径 - IP: {}, URI: {}", clientIp, requestUri);
                }
                return chain.filter(exchange).doFinally(onFinally);
            }

            // 检查白名单
            return rateLimitService.isInWhiteList(clientIp)
                    .flatMap(isWhiteListed -> {
                        if (isWhiteListed) {
                            if (rateLimitProperties.isVerboseLogging()) {
                                log.info("IP在白名单中，直接放行 - IP: {}", clientIp);
                            }
                            // 设置白名单属性到Exchange
                            exchange.getAttributes().put(AccessRecordContextKeys.WHITELIST_FLATMAP, true);
                            exchange.getAttributes().put(AccessRecordContextKeys.RATE_LIMITED, false);
                            return chain.filter(exchange).doFinally(onFinally);
                        } else {
                            // 设置不在白名单属性到Exchange
                            exchange.getAttributes().put(AccessRecordContextKeys.WHITELIST_FLATMAP, false);
                            // 检查黑名单
                            return checkBlackListAndProceed(exchange, chain, clientIp);
                        }
                    })
                    .onErrorResume(throwable -> {
                        // 区分不同类型的异常进行处理
                        if (isServiceDiscoveryException(throwable)) {
                            log.warn("检测到服务发现相关异常，跳过限流检查继续处理请求 - IP: {}, 异常: {}",
                                    clientIp, throwable.getMessage());
                            // 服务发现异常时直接放行，不影响业务
                            return chain.filter(exchange).doFinally(onFinally);
                        } else if (isRedisConnectionException(throwable)) {
                            log.error("Redis连接异常，允许请求通过以避免影响正常业务 - IP: {}, 错误: {}",
                                    clientIp, throwable.getMessage());
                            // Redis连接异常时允许请求通过
                            return chain.filter(exchange).doFinally(onFinally);
                        } else {
                            log.error("限流检查发生未知异常 - IP: {}, 错误: {}", clientIp, throwable.getMessage());
                            // 其他异常情况下也允许请求通过，避免影响正常业务
                            return chain.filter(exchange).doFinally(onFinally);
                        }
                    });
        });

    }

    /**
     * 判断是否应该跳过限流检查
     */
    private boolean shouldSkipRateLimit(String requestUri) {
        return Arrays.stream(rateLimitProperties.getSkipPaths())
                .anyMatch(pattern -> requestUri.matches(pattern.replace("**", ".*")));
    }

    /**
     * 检查黑名单并决定后续处理流程
     */
    private Mono<Void> checkBlackListAndProceed(ServerWebExchange exchange, GatewayFilterChain chain, String clientIp) {
        return rateLimitService.isInBlackList(clientIp)
                .flatMap(isInBlackList -> {
                    if (isInBlackList) {
                        if (rateLimitProperties.isVerboseLogging()) {
                            log.warn("IP在黑名单中，重定向到验证码页面 - IP: {}", clientIp);
                        }

                        // 设置黑名单限流属性到Exchange
                        exchange.getAttributes().put(AccessRecordContextKeys.RATE_LIMITED, true);
                        exchange.getAttributes().put(AccessRecordContextKeys.RATE_LIMIT_TYPE, "BLACKLIST_BLOCKED");
                        exchange.getAttributes().put(AccessRecordContextKeys.BLACKLIST_FLATMAP, true);

                        // 获取黑名单信息并记录日志
                        return rateLimitService.getBlackListInfo(clientIp)
                                .doOnNext(blacklistInfo -> {
                                    log.warn("黑名单IP访问被阻止 - IP: {}, 黑名单信息: {}", clientIp, blacklistInfo);

                                    // 设置黑名单信息到Exchange
                                    exchange.getAttributes().put(AccessRecordContextKeys.BLACKLIST_INFO, blacklistInfo);

                                    // 记录黑名单阻止日志到MongoDB
                                    rateLimitLogService.recordRateLimitLog(exchange, clientIp, "BLACKLIST_BLOCKED",
                                            "IP在黑名单中，需要验证码验证 - " + blacklistInfo,
                                            null, null, null);
                                })
                                .then(redirectToCaptcha(exchange));
                    } else {
                        if (rateLimitProperties.isVerboseLogging()) {
                            log.debug("IP不在黑名单中，继续限流检查 - IP: {}", clientIp);
                        }
                        // 设置不在黑名单属性到Exchange
                        exchange.getAttributes().put(AccessRecordContextKeys.BLACKLIST_FLATMAP, false);
                        // 不在黑名单中，执行正常的限流检查
                        return performRateLimitCheck(exchange, chain, clientIp);
                    }
                })
                .onErrorResume(throwable -> {
                    log.error("黑名单检查失败，允许请求通过 - IP: {}, 错误: {}", clientIp, throwable.getMessage());
                    // 黑名单检查异常时，继续执行限流检查，不影响正常业务
                    return performRateLimitCheck(exchange, chain, clientIp);
                });
    }

    /**
     * 执行限流检查
     */
    private Mono<Void> performRateLimitCheck(ServerWebExchange exchange, GatewayFilterChain chain, String clientIp) {
        // 记录IP访问
        return rateLimitService.recordIpAccess(clientIp)
                .then(checkRateLimitWithDetails(clientIp))
                .flatMap(result -> {
                    if (!result.isAllowed()) {
                        log.warn("IP限流触发 - IP: {}, 类型: {}", clientIp, result.getLimitType());
                        // 设置限流属性到Exchange
                        exchange.getAttributes().put(AccessRecordContextKeys.RATE_LIMITED, true);
                        exchange.getAttributes().put(AccessRecordContextKeys.RATE_LIMIT_TYPE, "IP_RATE_LIMIT");

                        // 记录IP限流日志到MongoDB - 使用正确的参数
                        rateLimitLogService.recordRateLimitLog(exchange, clientIp, "IP_RATE_LIMIT",
                                "IP访问频率超过限制 - " + result.getLimitType(),
                                result.getCurrentCount(), result.getThreshold(), result.getWindowSize());

                        // 将触发限流的IP添加到黑名单
                        return addToBlackListIfEnabled(clientIp, "IP访问频率超过限制 - " + result.getLimitType())
                                .then(redirectToCaptcha(exchange));
                    }
                    // 设置未限流属性到Exchange
                    exchange.getAttributes().put(AccessRecordContextKeys.RATE_LIMITED, false);
                    // 检查DDoS防护
                    return checkDDoSProtection(exchange, chain, clientIp);
                });
    }

    /**
     * 检查限流并返回详细信息
     */
    private Mono<RateLimitCheckResult> checkRateLimitWithDetails(String clientIp) {
        // 先检查分钟级限流
        return rateLimitService.checkMinuteRateLimitWithDetails(clientIp)
                .flatMap(minuteResult -> {
                    if (!minuteResult.isAllowed()) {
                        // 分钟级限流触发，转换结果格式
                        return Mono.just(new RateLimitCheckResult(
                                false,
                                minuteResult.getLimitType(),
                                minuteResult.getCurrentCount(),
                                minuteResult.getThreshold(),
                                minuteResult.getWindowSize()));
                    }
                    // 分钟级通过，检查秒级限流
                    return rateLimitService.checkSecondRateLimitWithDetails(clientIp)
                            .map(secondResult -> {
                                if (!secondResult.isAllowed()) {
                                    // 秒级限流触发
                                    return new RateLimitCheckResult(
                                            false,
                                            secondResult.getLimitType(),
                                            secondResult.getCurrentCount(),
                                            secondResult.getThreshold(),
                                            secondResult.getWindowSize());
                                }
                                // 都通过，返回允许的结果
                                return new RateLimitCheckResult(true, "NONE", null, null, null);
                            });
                });
    }

    /**
     * 检查限流 (保留原有方法用于向后兼容)
     */
    private Mono<Boolean> checkRateLimit(String clientIp) {
        return rateLimitService.checkMinuteRateLimit(clientIp)
                .flatMap(minuteAllowed -> {
                    if (!minuteAllowed) {
                        return Mono.just(false);
                    }
                    return rateLimitService.checkSecondRateLimit(clientIp);
                });
    }

    /**
     * 检查DDoS防护
     */
    private Mono<Void> checkDDoSProtection(ServerWebExchange exchange, GatewayFilterChain chain, String clientIp) {
        return rateLimitService.getActiveIpCount()
                .flatMap(ipCount -> {
                    if (rateLimitProperties.isVerboseLogging()) {
                        log.info("当前活跃IP数量: {}", ipCount);
                    }

                    return rateLimitService.isCaptchaRequired()
                            .flatMap(captchaRequired -> {
                                if (captchaRequired) {
                                    // 验证码机制已启用
                                    if (ipCount <= rateLimitProperties.getDdosReleaseIpCount()) {
                                        log.info("活跃IP数量降低，解除验证码机制 - 当前IP数: {}", ipCount);
                                        return rateLimitService.disableCaptchaRequired()
                                                .then(chain.filter(exchange).doFinally(onFinally));
                                    } else {
                                        log.info("验证码机制生效中，重定向到验证码页面 - IP: {}, 活跃IP数: {}", clientIp, ipCount);
                                        // 设置DDoS限流属性到Exchange
                                        exchange.getAttributes().put(AccessRecordContextKeys.RATE_LIMITED, true);
                                        exchange.getAttributes().put(AccessRecordContextKeys.RATE_LIMIT_TYPE,
                                                "DDOS_PROTECTION");

                                        // 记录DDoS防护日志到MongoDB
                                        rateLimitLogService.recordDdosLog(exchange, clientIp, "DDOS_PROTECTION",
                                                "验证码机制生效中，阻止访问", ipCount.intValue(),
                                                rateLimitProperties.getDdosReleaseIpCount());

                                        // 将持续访问的IP添加到黑名单
                                        return addToBlackListIfEnabled(clientIp, "验证码机制生效期间持续访问")
                                                .then(redirectToCaptcha(exchange));
                                    }
                                } else {
                                    // 检查是否需要启用验证码机制
                                    if (ipCount >= rateLimitProperties.getDdosThresholdIpCount()) {
                                        log.warn("检测到DDoS攻击，启用验证码机制 - 活跃IP数: {}, IP: {}", ipCount, clientIp);
                                        // 设置DDoS限流属性到Exchange
                                        exchange.getAttributes().put(AccessRecordContextKeys.RATE_LIMITED, true);
                                        exchange.getAttributes().put(AccessRecordContextKeys.RATE_LIMIT_TYPE,
                                                "DDOS_THRESHOLD");

                                        // 记录DDoS阈值触发日志到MongoDB
                                        rateLimitLogService.recordDdosLog(exchange, clientIp, "DDOS_THRESHOLD",
                                                "检测到DDoS攻击，启用验证码机制", ipCount.intValue(),
                                                rateLimitProperties.getDdosThresholdIpCount());

                                        // 启用验证码机制并将IP添加到黑名单
                                        return rateLimitService.enableCaptchaRequired()
                                                .then(addToBlackListIfEnabled(clientIp, "DDoS攻击触发"))
                                                .then(redirectToCaptcha(exchange));
                                    } else {
                                        // 正常放行
                                        return chain.filter(exchange).doFinally(onFinally);
                                    }
                                }
                            });
                });
    }

    /**
     * 重定向到验证码页面
     */
    private Mono<Void> redirectToCaptcha(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);

        String redirectUrl = buildCaptchaRedirectUrl(exchange);
        response.getHeaders().set(HttpHeaderConstants.REDIRECT_URL, redirectUrl);

        if (rateLimitProperties.isVerboseLogging()) {
            log.info("重定向到验证码页面: {}", redirectUrl);
        }

        return response.setComplete().doFinally(onFinally);
    }

    /**
     * 构建验证码页面的完整重定向URL
     */
    private String buildCaptchaRedirectUrl(ServerWebExchange exchange) {
        String captchaPath = rateLimitProperties.getCaptchaPagePath();

        // 如果已经是完整URL，直接返回
        if (isAbsoluteUrl(captchaPath)) {
            return captchaPath;
        }

        // 如果配置了baseUrl，使用baseUrl拼接
        String baseUrl = rateLimitProperties.getBaseUrl();
        if (baseUrl != null && !baseUrl.trim().isEmpty()) {
            // 确保baseUrl不以/结尾，captchaPath以/开头
            baseUrl = baseUrl.replaceAll("/$", "");
            captchaPath = captchaPath.startsWith("/") ? captchaPath : "/" + captchaPath;
            return baseUrl + captchaPath;
        }

        // 基于当前请求动态构建完整URL
        return buildUrlFromCurrentRequest(exchange, captchaPath);
    }

    /**
     * 判断是否为绝对URL
     */
    private boolean isAbsoluteUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    /**
     * 基于当前请求构建完整URL
     */
    private String buildUrlFromCurrentRequest(ServerWebExchange exchange, String path) {
        ServerHttpRequest request = exchange.getRequest();
        String scheme = request.getURI().getScheme();
        String host = request.getURI().getHost();
        int port = request.getURI().getPort();

        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(scheme).append("://").append(host);

        // 只有在非标准端口时才添加端口号
        if (port != -1 &&
                !("http".equals(scheme) && port == 80) &&
                !("https".equals(scheme) && port == 443)) {
            urlBuilder.append(":").append(port);
        }

        // 确保path以/开头
        if (!path.startsWith("/")) {
            urlBuilder.append("/");
        }
        urlBuilder.append(path);

        return urlBuilder.toString();
    }

    /**
     * 设置HTTP安全响应头
     */
    private void setHttpSecurityHeaders(ServerHttpResponse response) {
        HttpHeaders headers = response.getHeaders();

        // 防止XSS攻击
        headers.add(HttpHeaderConstants.X_XSS_PROTECTION, "1; mode=block");
        // 防止点击劫持
        headers.add(HttpHeaderConstants.X_FRAME_OPTIONS, "SAMEORIGIN");
        // 防止MIME类型嗅探
        headers.add(HttpHeaderConstants.X_CONTENT_TYPE_OPTIONS, "nosniff");
        // 控制Referer头
        headers.add(HttpHeaderConstants.REFERRER_POLICY, "no-referrer");
        // 内容安全策略
        headers.add(HttpHeaderConstants.CONTENT_SECURITY_POLICY,
                "default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; frame-src 'self';");
    }

    /**
     * 判断是否为服务发现相关异常
     */
    private boolean isServiceDiscoveryException(Throwable throwable) {
        String message = throwable.getMessage();
        return message != null && (message.contains("Unable to find instance") ||
                message.contains("SERVICE_UNAVAILABLE") ||
                message.contains("No instances") ||
                message.contains("LoadBalancer") ||
                message.contains("service discovery") ||
                throwable.getClass().getSimpleName().contains("LoadBalancer"));
    }

    /**
     * 判断是否为Redis连接相关异常
     */
    private boolean isRedisConnectionException(Throwable throwable) {
        String message = throwable.getMessage();
        return message != null && (message.contains("Connection reset") ||
                message.contains("Connection refused") ||
                message.contains("timeout") ||
                message.contains("Redis") ||
                throwable instanceof java.net.ConnectException ||
                throwable instanceof java.io.IOException ||
                throwable instanceof java.util.concurrent.TimeoutException);
    }

    /**
     * 如果黑名单功能启用，则将IP添加到黑名单
     * 
     * @param clientIp 客户端IP
     * @param reason   添加原因
     * @return Mono<Void>
     */
    private Mono<Void> addToBlackListIfEnabled(String clientIp, String reason) {
        if (!rateLimitProperties.isBlackListEnabled()) {
            if (rateLimitProperties.isVerboseLogging()) {
                log.debug("黑名单功能未启用，跳过添加黑名单 - IP: {}", clientIp);
            }
            return Mono.empty();
        }

        return rateLimitService.addToBlackList(clientIp, reason, rateLimitProperties.getBlackListDurationMinutes())
                .doOnNext(success -> {
                    if (success) {
                        log.warn("IP已添加到黑名单 - IP: {}, 原因: {}, 有效期: {}分钟",
                                clientIp, reason, rateLimitProperties.getBlackListDurationMinutes());
                    } else {
                        log.error("添加IP到黑名单失败 - IP: {}, 原因: {}", clientIp, reason);
                    }
                })
                .onErrorResume(throwable -> {
                    log.error("添加IP到黑名单异常 - IP: {}, 原因: {}, 错误: {}",
                            clientIp, reason, throwable.getMessage());
                    return Mono.just(false); // 异常时不影响主流程
                })
                .then();
    }

    @Override
    public int getOrder() {
        return GlobalFilterOrderConfig.DDOS_RATE_LIMIT_FILTER_ORDER; // 使用统一的全局过滤器顺序管理
    }
}