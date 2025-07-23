package com.dev.gateway.filter.ratelimit.configuration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 限流模块配置类
 * 提供Redis相关的Bean配置和IP解析器配置
 */
@Configuration
public class RateLimitConfiguration {

    /**
     * 配置限流专用的ReactiveRedisTemplate
     * 使用String序列化器确保数据在Redis中的可读性
     *
     * 	- rateLimitRedisTemplate: defined by method 'rateLimitRedisTemplate' in class path resource [com/dev/gateway/ratelimit/configuration/RateLimitConfiguration.class]
     * 	- reactiveStringRedisTemplate: defined by method 'reactiveStringRedisTemplate' in class path resource [org/springframework/boot/autoconfigure/data/redis/RedisReactiveAutoConfiguration.class]
     */
    @Bean("rateLimitRedisTemplate")
    @Qualifier("rateLimitRedisTemplate")
    @Primary
    public ReactiveRedisTemplate<String, String> rateLimitRedisTemplate(
            ReactiveRedisConnectionFactory reactiveRedisConnectionFactory) {
        
        // 使用String序列化器
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        
        // 构建序列化上下文
        RedisSerializationContext<String, String> serializationContext = 
                RedisSerializationContext.<String, String>newSerializationContext()
                        .key(stringRedisSerializer)
                        .value(stringRedisSerializer)
                        .hashKey(stringRedisSerializer)
                        .hashValue(stringRedisSerializer)
                        .build();
        
        return new ReactiveRedisTemplate<>(reactiveRedisConnectionFactory, serializationContext);
    }

    /**
     * 配置IP地址解析器
     * 支持从X-Forwarded-For, X-Real-IP等头部获取真实IP
     */
    @Bean("rateLimitIpResolver")
    public XForwardedRemoteAddressResolver xForwardedRemoteAddressResolver() {
        return XForwardedRemoteAddressResolver.maxTrustedIndex(1);
    }
} 