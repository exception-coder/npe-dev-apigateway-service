package com.dev.gateway.framework.handler;

import org.springframework.core.Ordered;
import org.springframework.web.reactive.handler.AbstractHandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Deprecated
public class ApiGatewayHandlerMapping extends AbstractHandlerMapping {


    public ApiGatewayHandlerMapping() {
        this.setOrder(Ordered.HIGHEST_PRECEDENCE);
    }

    @Override
    protected Mono<?> getHandlerInternal(ServerWebExchange exchange) {
        return Mono.empty();
    }

}
