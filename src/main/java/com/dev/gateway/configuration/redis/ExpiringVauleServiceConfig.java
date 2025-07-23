package com.dev.gateway.configuration.redis;

import com.dev.gateway.service.redis.ExpiringValueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.ReactiveRedisTemplate;

import java.time.Duration;

@Slf4j
@Configuration
public class ExpiringVauleServiceConfig {

    private final ReactiveRedisTemplate<String, String> rateLimitRedisTemplate;


    public ExpiringVauleServiceConfig(ReactiveRedisTemplate<String, String> rateLimitRedisTemplate) {
        this.rateLimitRedisTemplate = rateLimitRedisTemplate;
    }


    /**
     * 验证码机制出发后ip和验证码对应关系
     * @return
     */
    @Bean
    public ExpiringValueService ipWithCaptcha1Minutes() {
        return new ExpiringValueService(this.rateLimitRedisTemplate,"ipWithCaptcha1Minutes", Duration.ofMinutes(1));
    }


    /**
     * 验证码机制触发后的白名单ip
     *
     * @return
     */
    @Bean
    public ExpiringValueService whiteList5minutes() {
        return new ExpiringValueService(this.rateLimitRedisTemplate,"whiteList5minutes", Duration.ofMinutes(5));
    }

    /**
     *
     * 系统是否启用验证码机制
     *
     */
    @Bean
    public ExpiringValueService captchaRequired() {
        return new ExpiringValueService(this.rateLimitRedisTemplate,"captchaRequired", Duration.ofMinutes(5));
    }

}
