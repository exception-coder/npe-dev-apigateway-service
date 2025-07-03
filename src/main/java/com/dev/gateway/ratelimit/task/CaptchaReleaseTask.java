package com.dev.gateway.ratelimit.task;

import com.dev.gateway.ratelimit.properties.RateLimitProperties;
import com.dev.gateway.ratelimit.service.RateLimitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 验证码自动解除定时任务
 * 定期检查活跃IP数量，当数量降低到释放阈值以下时自动解除验证码机制
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "gateway.rate-limit.captcha-auto-release", havingValue = "true", matchIfMissing = true)
public class CaptchaReleaseTask {

    private final RateLimitService rateLimitService;
    private final RateLimitProperties rateLimitProperties;

    public CaptchaReleaseTask(RateLimitService rateLimitService, RateLimitProperties rateLimitProperties) {
        this.rateLimitService = rateLimitService;
        this.rateLimitProperties = rateLimitProperties;
    }

    /**
     * 每30秒检查一次验证码解除条件
     */
    @Scheduled(fixedRate = 30000)
    public void checkCaptchaReleaseCondition() {
        if (!rateLimitProperties.isEnabled()) {
            return;
        }

        rateLimitService.isCaptchaRequired()
                .flatMap(captchaRequired -> {
                    if (Boolean.TRUE.equals(captchaRequired)) {
                        // 验证码已激活，检查是否可以解除
                        return rateLimitService.getActiveIpCount()
                                .flatMap(ipCount -> {
                                    if (ipCount <= rateLimitProperties.getDdosReleaseIpCount()) {
                                        log.info("活跃IP数量降低到释放阈值以下，自动解除验证码机制 - 当前IP数: {}, 释放阈值: {}",
                                                ipCount, rateLimitProperties.getDdosReleaseIpCount());
                                        return rateLimitService.disableCaptchaRequired();
                                    } else {
                                        if (rateLimitProperties.isVerboseLogging()) {
                                            log.debug("验证码机制仍然激活 - 当前IP数: {}, 释放阈值: {}",
                                                    ipCount, rateLimitProperties.getDdosReleaseIpCount());
                                        }
                                        return Mono.just(false);
                                    }
                                });
                    } else {
                        // 验证码未激活，无需检查
                        return Mono.just(false);
                    }
                })
                .doOnError(throwable -> {
                    log.error("验证码解除检查失败: {}", throwable.getMessage(), throwable);
                })
                .subscribe(); // 异步执行，不阻塞
    }
}