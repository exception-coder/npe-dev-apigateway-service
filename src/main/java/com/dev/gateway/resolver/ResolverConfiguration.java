package com.dev.gateway.resolver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

@Configuration
public class ResolverConfiguration {

    @Bean
    public XForwardedRemoteAddressResolver xForwardedRemoteAddressResolver(@Value("${app.xForwardedFor.maxTrustedIndex:1}") Integer maxTrustedIndex) {
        //代表信任 XForwarded 中网络跳转数量
        return XForwardedRemoteAddressResolver.maxTrustedIndex(maxTrustedIndex);
    }

    @Primary
    @Bean
    public KeyResolver ipKeyResolver(XForwardedRemoteAddressResolver xForwardedRemoteAddressResolver) {
        return exchange -> Mono.just(xForwardedRemoteAddressResolver.resolve(exchange).getAddress().getHostAddress()+exchange.getRequest().getPath());
    }

}
