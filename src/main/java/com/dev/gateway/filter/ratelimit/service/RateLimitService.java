package com.dev.gateway.filter.ratelimit.service;

import com.dev.gateway.filter.ratelimit.properties.RateLimitProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * 限流服务类
 * 使用滑动窗口算法封装所有Redis相关的限流操作
 */
@Service
@Slf4j
public class RateLimitService {

    @Autowired
    @Qualifier("rateLimitRedisTemplate")
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Autowired
    private RateLimitProperties rateLimitProperties;

    // Redis键模板 - 改为滑动窗口使用ZSET
    private static final String SLIDING_WINDOW_TEMPLATE = "%s:sliding_window:%s:%s";
    private static final String IP_ACCESS_TEMPLATE = "%s:ip_access:%s";
    private static final String WHITE_LIST_TEMPLATE = "%s:white_list:%s";
    private static final String BLACK_LIST_TEMPLATE = "%s:black_list:%s";
    private static final String CAPTCHA_REQUIRED_TEMPLATE = "%s:captcha_required";
    private static final String IP_CAPTCHA_TEMPLATE = "%s:ip_captcha:%s";

    // 超时和重试配置
    private static final Duration REDIS_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_RETRY_ATTEMPTS = 3;

    /**
     * 限流检查结果类
     */
    public static class RateLimitResult {
        private final boolean allowed;
        private final String limitType;
        private final Integer currentCount;
        private final Integer threshold;
        private final Integer windowSize;

        public RateLimitResult(boolean allowed, String limitType, Integer currentCount, Integer threshold,
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

    /**
     * 滑动窗口限流检查（每分钟）
     */
    public Mono<Boolean> checkMinuteRateLimit(String clientIp) {
        return slidingWindowRateLimit(clientIp, "minute", 60, rateLimitProperties.getMaxRequestsPerMinute());
    }

    /**
     * 滑动窗口限流检查（每秒）
     */
    public Mono<Boolean> checkSecondRateLimit(String clientIp) {
        return slidingWindowRateLimit(clientIp, "second", 1, rateLimitProperties.getMaxRequestsPerSecond());
    }

    /**
     * 检查分钟级限流并返回详细结果
     */
    public Mono<RateLimitResult> checkMinuteRateLimitWithDetails(String clientIp) {
        return slidingWindowRateLimitWithDetails(clientIp, "minute", 60, rateLimitProperties.getMaxRequestsPerMinute());
    }

    /**
     * 检查秒级限流并返回详细结果
     */
    public Mono<RateLimitResult> checkSecondRateLimitWithDetails(String clientIp) {
        return slidingWindowRateLimitWithDetails(clientIp, "second", 1, rateLimitProperties.getMaxRequestsPerSecond());
    }

    /**
     * 滑动窗口限流核心算法
     * 
     * @param clientIp          客户端IP
     * @param windowType        窗口类型（minute/second）
     * @param windowSizeSeconds 窗口大小（秒）
     * @param maxRequests       最大请求数
     * @return 是否允许请求通过
     */
    private Mono<Boolean> slidingWindowRateLimit(String clientIp, String windowType, int windowSizeSeconds,
            int maxRequests) {
        return slidingWindowRateLimitWithDetails(clientIp, windowType, windowSizeSeconds, maxRequests)
                .map(RateLimitResult::isAllowed);
    }

    /**
     * 滑动窗口限流核心算法（返回详细信息）
     * 
     * @param clientIp          客户端IP
     * @param windowType        窗口类型（minute/second）
     * @param windowSizeSeconds 窗口大小（秒）
     * @param maxRequests       最大请求数
     * @return 限流检查结果
     */
    private Mono<RateLimitResult> slidingWindowRateLimitWithDetails(String clientIp, String windowType,
            int windowSizeSeconds, int maxRequests) {
        String key = String.format(SLIDING_WINDOW_TEMPLATE,
                rateLimitProperties.getRedisKeyPrefix(), windowType, clientIp);

        long now = Instant.now().toEpochMilli();
        long windowStart = now - (windowSizeSeconds * 1000L);

        return redisTemplate.opsForZSet()
                // 1. 删除窗口外的过期记录
                .removeRangeByScore(key, Range.closed(0D, (double) windowStart))
                .timeout(REDIS_TIMEOUT)
                .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, Duration.ofMillis(100))
                        .filter(this::isRetryableException))
                .then(
                        // 2. 统计窗口内的请求数量
                        redisTemplate.opsForZSet()
                                .count(key, Range.open((double) windowStart, (double) now))
                                .timeout(REDIS_TIMEOUT))
                .flatMap(currentCount -> {
                    boolean allowed = currentCount < maxRequests; // 注意这里用 < 而不是 <=，因为会在后面添加请求
                    String limitType = windowType.toUpperCase() + "_LIMIT";

                    if (allowed) {
                        // 3. 如果允许，添加当前请求记录
                        String requestId = UUID.randomUUID().toString();
                        return redisTemplate.opsForZSet()
                                .add(key, requestId, now)
                                .timeout(REDIS_TIMEOUT)
                                .then(
                                        // 4. 设置键的过期时间
                                        redisTemplate.expire(key, Duration.ofSeconds(windowSizeSeconds + 1))
                                                .timeout(REDIS_TIMEOUT))
                                .then(Mono.just(new RateLimitResult(true, limitType,
                                        currentCount.intValue() + 1, maxRequests, windowSizeSeconds)));
                    } else {
                        // 被限流，不添加请求记录
                        return Mono.just(new RateLimitResult(false, limitType,
                                currentCount.intValue(), maxRequests, windowSizeSeconds));
                    }
                })
                .doOnNext(result -> {
                    if (rateLimitProperties.isVerboseLogging()) {
                        log.debug("滑动窗口{}限流检查 - IP: {}, 窗口: {}秒, 当前计数: {}/{}, 允许: {}",
                                windowType, clientIp, windowSizeSeconds, result.getCurrentCount(),
                                result.getThreshold(), result.isAllowed());
                    }
                })
                .doOnError(throwable -> {
                    log.error("Redis滑动窗口{}限流检查失败 - IP: {}, 错误: {}",
                            windowType, clientIp, throwable.getMessage());
                })
                .onErrorReturn(new RateLimitResult(true, "ERROR", null, null, null)); // 异常时允许请求通过
    }

    /**
     * 获取指定窗口内的当前请求计数
     */
    public Mono<Integer> getCurrentRequestCount(String clientIp, String windowType, int windowSizeSeconds) {
        String key = String.format(SLIDING_WINDOW_TEMPLATE,
                rateLimitProperties.getRedisKeyPrefix(), windowType, clientIp);

        long now = Instant.now().toEpochMilli();
        long windowStart = now - (windowSizeSeconds * 1000L);

        return redisTemplate.opsForZSet()
                // 删除过期记录
                .removeRangeByScore(key, Range.closed(0D, (double) windowStart))
                .timeout(REDIS_TIMEOUT)
                .then(
                        // 统计当前窗口内的请求数量
                        redisTemplate.opsForZSet()
                                .count(key, Range.open((double) windowStart, (double) now))
                                .timeout(REDIS_TIMEOUT))
                .map(Long::intValue)
                .doOnError(throwable -> {
                    log.error("获取请求计数失败 - IP: {}, 窗口类型: {}, 错误: {}",
                            clientIp, windowType, throwable.getMessage());
                })
                .onErrorReturn(0);
    }

    /**
     * 记录IP访问（滑动窗口方式）
     */
    public Mono<Void> recordIpAccess(String clientIp) {
        String key = String.format(IP_ACCESS_TEMPLATE, rateLimitProperties.getRedisKeyPrefix(), clientIp);
        long timestamp = Instant.now().toEpochMilli();
        String requestId = UUID.randomUUID().toString();

        int trackDurationSeconds = rateLimitProperties.getIpTrackDurationSeconds();
        long windowStart = timestamp - (trackDurationSeconds * 1000L);

        return redisTemplate.opsForZSet()
                // 删除过期记录
                .removeRangeByScore(key, Range.closed(0D, (double) windowStart))
                .timeout(REDIS_TIMEOUT)
                .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, Duration.ofMillis(100))
                        .filter(this::isRetryableException))
                .then(
                        // 添加当前访问记录
                        redisTemplate.opsForZSet()
                                .add(key, requestId, timestamp)
                                .timeout(REDIS_TIMEOUT))
                .then(
                        // 设置过期时间
                        redisTemplate.expire(key, Duration.ofSeconds(trackDurationSeconds + 1))
                                .timeout(REDIS_TIMEOUT))
                .then()
                .doOnError(throwable -> {
                    log.error("Redis记录IP访问失败 - IP: {}, 错误: {}", clientIp, throwable.getMessage());
                })
                .onErrorResume(throwable -> Mono.empty()); // 异常时忽略，不影响主流程
    }

    /**
     * 获取活跃IP数量（滑动窗口方式）
     */
    public Mono<Long> getActiveIpCount() {
        String pattern = String.format(IP_ACCESS_TEMPLATE, rateLimitProperties.getRedisKeyPrefix(), "*");
        int trackDurationSeconds = rateLimitProperties.getIpTrackDurationSeconds();
        long now = Instant.now().toEpochMilli();
        long windowStart = now - (trackDurationSeconds * 1000L);

        return redisTemplate.keys(pattern)
                .flatMap((String key) ->
                // 清理过期记录并统计活跃记录
                redisTemplate.opsForZSet()
                        .removeRangeByScore(key, Range.closed(0D, (double) windowStart))
                        .then(redisTemplate.opsForZSet().count(key, Range.open((double) windowStart, (double) now)))
                        .map(count -> count > 0 ? 1L : 0L))
                .reduce(0L, Long::sum)
                .timeout(REDIS_TIMEOUT)
                .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, Duration.ofMillis(100))
                        .filter(this::isRetryableException))
                .doOnError(throwable -> {
                    log.error("Redis获取活跃IP数量失败 - 错误: {}", throwable.getMessage());
                })
                .onErrorReturn(0L); // 异常时返回0
    }

    /**
     * 检查IP是否在白名单中
     * 添加超时处理和重试机制
     */
    public Mono<Boolean> isInWhiteList(String clientIp) {
        String key = String.format(WHITE_LIST_TEMPLATE, rateLimitProperties.getRedisKeyPrefix(), clientIp);

        return redisTemplate.hasKey(key)
                .timeout(REDIS_TIMEOUT)
                .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, Duration.ofMillis(100))
                        .filter(this::isRetryableException))
                .doOnNext(result -> {
                    if (rateLimitProperties.isVerboseLogging()) {
                        log.debug("白名单检查结果 - IP: {}, 在白名单: {}", clientIp, result);
                    }
                })
                .doOnError(throwable -> {
                    log.error("Redis白名单检查失败 - IP: {}, 错误: {}", clientIp, throwable.getMessage());
                })
                .onErrorReturn(false); // 异常时默认返回false，不在白名单中
    }

    /**
     * 将IP添加到白名单
     */
    public Mono<Boolean> addToWhiteList(String clientIp) {
        String key = String.format(WHITE_LIST_TEMPLATE, rateLimitProperties.getRedisKeyPrefix(), clientIp);
        Duration expiry = Duration.ofMinutes(rateLimitProperties.getWhiteListDurationMinutes());

        return redisTemplate.opsForValue()
                .set(key, "verified", expiry);
    }

    /**
     * 从白名单移除IP
     */
    public Mono<Boolean> removeFromWhiteList(String clientIp) {
        String key = String.format(WHITE_LIST_TEMPLATE, rateLimitProperties.getRedisKeyPrefix(), clientIp);
        return redisTemplate.delete(key).map(count -> count > 0);
    }

    /**
     * 检查IP是否在黑名单中
     * 添加超时处理和重试机制
     */
    public Mono<Boolean> isInBlackList(String clientIp) {
        String key = String.format(BLACK_LIST_TEMPLATE, rateLimitProperties.getRedisKeyPrefix(), clientIp);

        return redisTemplate.hasKey(key)
                .timeout(REDIS_TIMEOUT)
                .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, Duration.ofMillis(100))
                        .filter(this::isRetryableException))
                .doOnNext(result -> {
                    if (rateLimitProperties.isVerboseLogging()) {
                        log.debug("黑名单检查结果 - IP: {}, 在黑名单: {}", clientIp, result);
                    }
                })
                .doOnError(throwable -> {
                    log.error("Redis黑名单检查失败 - IP: {}, 错误: {}", clientIp, throwable.getMessage());
                })
                .onErrorReturn(false); // 异常时默认返回false，不在黑名单中
    }

    /**
     * 将IP添加到黑名单
     * 
     * @param clientIp        客户端IP
     * @param reason          加入黑名单的原因
     * @param durationMinutes 黑名单有效期（分钟）
     */
    public Mono<Boolean> addToBlackList(String clientIp, String reason, int durationMinutes) {
        String key = String.format(BLACK_LIST_TEMPLATE, rateLimitProperties.getRedisKeyPrefix(), clientIp);
        Duration expiry = Duration.ofMinutes(durationMinutes);

        // 存储原因和时间戳
        String value = String.format("reason:%s,timestamp:%d", reason, System.currentTimeMillis());

        return redisTemplate.opsForValue()
                .set(key, value, expiry)
                .timeout(REDIS_TIMEOUT)
                .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, Duration.ofMillis(100))
                        .filter(this::isRetryableException))
                .doOnNext(result -> {
                    if (result) {
                        log.warn("IP已添加到黑名单 - IP: {}, 原因: {}, 有效期: {}分钟", clientIp, reason, durationMinutes);
                    }
                })
                .doOnError(throwable -> {
                    log.error("Redis添加黑名单失败 - IP: {}, 错误: {}", clientIp, throwable.getMessage());
                })
                .onErrorReturn(false); // 异常时返回false
    }

    /**
     * 从黑名单移除IP
     */
    public Mono<Boolean> removeFromBlackList(String clientIp) {
        String key = String.format(BLACK_LIST_TEMPLATE, rateLimitProperties.getRedisKeyPrefix(), clientIp);

        return redisTemplate.delete(key)
                .map(count -> count > 0)
                .timeout(REDIS_TIMEOUT)
                .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, Duration.ofMillis(100))
                        .filter(this::isRetryableException))
                .doOnNext(result -> {
                    if (result) {
                        log.info("IP已从黑名单移除 - IP: {}", clientIp);
                    }
                })
                .doOnError(throwable -> {
                    log.error("Redis移除黑名单失败 - IP: {}, 错误: {}", clientIp, throwable.getMessage());
                })
                .onErrorReturn(false); // 异常时返回false
    }

    /**
     * 获取黑名单IP信息
     * 
     * @param clientIp 客户端IP
     * @return 黑名单信息（包含原因和时间戳）
     */
    public Mono<String> getBlackListInfo(String clientIp) {
        String key = String.format(BLACK_LIST_TEMPLATE, rateLimitProperties.getRedisKeyPrefix(), clientIp);

        return redisTemplate.opsForValue()
                .get(key)
                .timeout(REDIS_TIMEOUT)
                .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, Duration.ofMillis(100))
                        .filter(this::isRetryableException))
                .doOnError(throwable -> {
                    log.error("Redis获取黑名单信息失败 - IP: {}, 错误: {}", clientIp, throwable.getMessage());
                })
                .onErrorReturn(""); // 异常时返回空字符串
    }

    /**
     * 检查是否需要验证码
     */
    public Mono<Boolean> isCaptchaRequired() {
        String key = String.format(CAPTCHA_REQUIRED_TEMPLATE, rateLimitProperties.getRedisKeyPrefix());
        return redisTemplate.hasKey(key)
                .timeout(REDIS_TIMEOUT)
                .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, Duration.ofMillis(100))
                        .filter(this::isRetryableException))
                .doOnError(throwable -> {
                    log.error("Redis检查验证码状态失败 - 错误: {}", throwable.getMessage());
                })
                .onErrorReturn(false); // 异常时默认不需要验证码
    }

    /**
     * 启用验证码机制
     */
    public Mono<Boolean> enableCaptchaRequired() {
        String key = String.format(CAPTCHA_REQUIRED_TEMPLATE, rateLimitProperties.getRedisKeyPrefix());
        Duration expiry = Duration.ofMinutes(rateLimitProperties.getCaptchaDurationMinutes());

        return redisTemplate.opsForValue()
                .set(key, "true", expiry)
                .timeout(REDIS_TIMEOUT)
                .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, Duration.ofMillis(100))
                        .filter(this::isRetryableException))
                .doOnError(throwable -> {
                    log.error("Redis启用验证码机制失败 - 错误: {}", throwable.getMessage());
                })
                .onErrorReturn(false); // 异常时返回false
    }

    /**
     * 禁用验证码机制
     */
    public Mono<Boolean> disableCaptchaRequired() {
        String key = String.format(CAPTCHA_REQUIRED_TEMPLATE, rateLimitProperties.getRedisKeyPrefix());
        return redisTemplate.delete(key)
                .map(count -> count > 0)
                .timeout(REDIS_TIMEOUT)
                .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, Duration.ofMillis(100))
                        .filter(this::isRetryableException))
                .doOnError(throwable -> {
                    log.error("Redis禁用验证码机制失败 - 错误: {}", throwable.getMessage());
                })
                .onErrorReturn(false); // 异常时返回false
    }

    /**
     * 判断异常是否需要重试
     */
    private boolean isRetryableException(Throwable throwable) {
        return throwable instanceof java.net.ConnectException ||
                throwable instanceof java.io.IOException ||
                throwable instanceof java.util.concurrent.TimeoutException ||
                throwable.getMessage().contains("Connection reset") ||
                throwable.getMessage().contains("timeout") ||
                throwable.getMessage().contains("Connection refused");
    }

    /**
     * 清理过期的限流计数器（可选的维护方法）
     */
    public Mono<Long> cleanupExpiredCounters() {
        String pattern = rateLimitProperties.getRedisKeyPrefix() + ":ip_counter:*";
        return redisTemplate.keys(pattern)
                .filterWhen(key -> redisTemplate.getExpire(key).map(ttl -> ttl.isNegative()))
                .collectList()
                .flatMap(expiredKeys -> {
                    if (expiredKeys.isEmpty()) {
                        return Mono.just(0L);
                    }
                    return redisTemplate.delete(expiredKeys.toArray(new String[0]));
                });
    }

    /**
     * 获取限流统计信息
     */
    public Mono<RateLimitStats> getStats() {
        return Mono.fromCallable(() -> {
            RateLimitStats stats = new RateLimitStats();

            // 获取活跃IP数量
            getActiveIpCount().subscribe(stats::setActiveIpCount);

            // 获取验证码状态
            isCaptchaRequired().subscribe(stats::setCaptchaRequired);

            return stats;
        });
    }

    /**
     * 限流统计信息类
     */
    public static class RateLimitStats {
        private Long activeIpCount = 0L;
        private Boolean captchaRequired = false;

        public Long getActiveIpCount() {
            return activeIpCount;
        }

        public void setActiveIpCount(Long activeIpCount) {
            this.activeIpCount = activeIpCount;
        }

        public Boolean getCaptchaRequired() {
            return captchaRequired;
        }

        public void setCaptchaRequired(Boolean captchaRequired) {
            this.captchaRequired = captchaRequired;
        }
    }

    /**
     * 记录最近10秒内访问的IP（替代RateLimiterCacheService.setLast10SecondsReqIp）
     * 使用滑动窗口方式
     */
    public Mono<Boolean> recordLast10SecondsReqIp(String clientIp) {
        String key = String.format("%s:last_10s_ip:%s", rateLimitProperties.getRedisKeyPrefix(), clientIp);
        long timestamp = Instant.now().toEpochMilli();
        long windowStart = timestamp - 10000L; // 10秒前
        String requestId = UUID.randomUUID().toString();

        return redisTemplate.opsForZSet()
                // 删除10秒前的记录
                .removeRangeByScore(key, Range.closed(0D, (double) windowStart))
                .timeout(REDIS_TIMEOUT)
                .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, Duration.ofMillis(100))
                        .filter(this::isRetryableException))
                .then(
                        // 添加当前记录
                        redisTemplate.opsForZSet()
                                .add(key, requestId, timestamp)
                                .timeout(REDIS_TIMEOUT))
                .then(
                        // 设置过期时间
                        redisTemplate.expire(key, Duration.ofSeconds(11))
                                .timeout(REDIS_TIMEOUT))
                .doOnError(throwable -> {
                    log.error("Redis记录最近10秒IP失败 - IP: {}, 错误: {}", clientIp, throwable.getMessage());
                })
                .onErrorReturn(false);
    }

    /**
     * 获取最近10秒内访问的IP数量（替代RateLimiterCacheService.getLast10SecondsReqIpCount）
     * 使用滑动窗口方式
     */
    public Mono<Long> getLast10SecondsReqIpCount() {
        String pattern = String.format("%s:last_10s_ip:*", rateLimitProperties.getRedisKeyPrefix());
        long now = Instant.now().toEpochMilli();
        long windowStart = now - 10000L; // 10秒前

        return redisTemplate.keys(pattern)
                .flatMap((String key) ->
                // 清理过期记录并统计活跃IP
                redisTemplate.opsForZSet()
                        .removeRangeByScore(key, Range.closed(0D, (double) windowStart))
                        .then(redisTemplate.opsForZSet().count(key, Range.open((double) windowStart, (double) now)))
                        .map(count -> count > 0 ? 1L : 0L))
                .reduce(0L, Long::sum)
                .timeout(REDIS_TIMEOUT)
                .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, Duration.ofMillis(100))
                        .filter(this::isRetryableException))
                .doOnError(throwable -> {
                    log.error("Redis获取最近10秒IP数量失败 - 错误: {}", throwable.getMessage());
                })
                .onErrorReturn(0L);
    }

    /**
     * 设置IP验证码关联（替代RateLimiterCacheService.setIpWithCaptcha1Minutes）
     */
    public Mono<Boolean> setIpWithCaptcha1Minutes(String clientIp, String captchaText) {
        String key = String.format(IP_CAPTCHA_TEMPLATE, rateLimitProperties.getRedisKeyPrefix(), clientIp);
        Duration expiry = Duration.ofMinutes(1);

        return redisTemplate.opsForValue()
                .set(key, captchaText, expiry)
                .timeout(REDIS_TIMEOUT)
                .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, Duration.ofMillis(100))
                        .filter(this::isRetryableException))
                .doOnError(throwable -> {
                    log.error("Redis设置IP验证码关联失败 - IP: {}, 错误: {}", clientIp, throwable.getMessage());
                })
                .onErrorReturn(false);
    }

    /**
     * 获取IP关联的验证码（替代RateLimiterCacheService.getCaptcha1MinutesWithIp）
     */
    public Mono<String> getCaptcha1MinutesWithIp(String clientIp) {
        String key = String.format(IP_CAPTCHA_TEMPLATE, rateLimitProperties.getRedisKeyPrefix(), clientIp);

        return redisTemplate.opsForValue()
                .get(key)
                .timeout(REDIS_TIMEOUT)
                .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, Duration.ofMillis(100))
                        .filter(this::isRetryableException))
                .doOnError(throwable -> {
                    log.error("Redis获取IP验证码关联失败 - IP: {}, 错误: {}", clientIp, throwable.getMessage());
                })
                .onErrorReturn("");
    }

    /**
     * 设置验证码必需状态（替代RateLimiterCacheService.setCaptchaRequired）
     */
    public Mono<Boolean> setCaptchaRequired() {
        String key = String.format(CAPTCHA_REQUIRED_TEMPLATE, rateLimitProperties.getRedisKeyPrefix());
        Duration expiry = Duration.ofMinutes(rateLimitProperties.getCaptchaDurationMinutes());

        return redisTemplate.opsForValue()
                .set(key, "true", expiry)
                .timeout(REDIS_TIMEOUT)
                .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, Duration.ofMillis(100))
                        .filter(this::isRetryableException))
                .doOnError(throwable -> {
                    log.error("Redis设置验证码必需状态失败 - 错误: {}", throwable.getMessage());
                })
                .onErrorReturn(false);
    }

    /**
     * 获取验证码必需状态（替代RateLimiterCacheService.getCaptchaRequired）
     */
    public Mono<Boolean> getCaptchaRequired() {
        String key = String.format(CAPTCHA_REQUIRED_TEMPLATE, rateLimitProperties.getRedisKeyPrefix());

        return redisTemplate.hasKey(key)
                .timeout(REDIS_TIMEOUT)
                .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, Duration.ofMillis(100))
                        .filter(this::isRetryableException))
                .doOnError(throwable -> {
                    log.error("Redis获取验证码必需状态失败 - 错误: {}", throwable.getMessage());
                })
                .onErrorReturn(false);
    }

    /**
     * 设置白名单IP（替代RateLimiterCacheService.setWhiteList5minutes）
     */
    public Mono<Boolean> setWhiteList5minutes(String clientIp) {
        String key = String.format(WHITE_LIST_TEMPLATE, rateLimitProperties.getRedisKeyPrefix(), clientIp);
        Duration expiry = Duration.ofMinutes(5);

        return redisTemplate.opsForValue()
                .set(key, clientIp, expiry)
                .timeout(REDIS_TIMEOUT)
                .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, Duration.ofMillis(100))
                        .filter(this::isRetryableException))
                .doOnError(throwable -> {
                    log.error("Redis设置白名单失败 - IP: {}, 错误: {}", clientIp, throwable.getMessage());
                })
                .onErrorReturn(false);
    }

    /**
     * 获取白名单IP
     */
    public Mono<String> getWhiteList(String clientIp) {
        String key = String.format(WHITE_LIST_TEMPLATE, rateLimitProperties.getRedisKeyPrefix(), clientIp);

        return redisTemplate.opsForValue()
                .get(key)
                .timeout(REDIS_TIMEOUT)
                .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, Duration.ofMillis(100))
                        .filter(this::isRetryableException))
                .doOnError(throwable -> {
                    log.error("Redis获取白名单失败 - IP: {}, 错误: {}", clientIp, throwable.getMessage());
                })
                .onErrorReturn("");
    }
}