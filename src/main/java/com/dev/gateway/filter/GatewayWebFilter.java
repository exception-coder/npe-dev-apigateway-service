package com.dev.gateway.filter;

import com.dev.gateway.service.IpResolverService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class GatewayWebFilter implements WebFilter {

    private final IpResolverService ipResolverService;

    public GatewayWebFilter(IpResolverService ipResolverService) {
        this.ipResolverService = ipResolverService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ipResolverService.getClientIp(exchange);
        return chain.filter(exchange);
    }
}