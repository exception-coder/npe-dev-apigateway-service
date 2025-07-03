package com.dev.gateway.controller;

import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson.JSON;
import com.dev.gateway.properties.RouteProperties;
import com.dev.gateway.service.redis.ExpiringValueService;
import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
public class WebController {
    private final ExpiringValueService ipSetLast10Seconds;

    private final ExpiringValueService ipWithCaptcha1Minutes;

    private final ExpiringValueService whiteList5minutes;

    private final RouteProperties routeProperties;

    public WebController(@Qualifier("ipSetLast10Seconds") ExpiringValueService ipSetLast10Seconds,
                         @Qualifier("ipWithCaptcha1Minutes") ExpiringValueService ipWithCaptcha1Minutes,
                         @Qualifier("whiteList5minutes") ExpiringValueService whiteList5minutes, RouteProperties routeProperties) {
        this.ipSetLast10Seconds = ipSetLast10Seconds;
        this.ipWithCaptcha1Minutes = ipWithCaptcha1Minutes;
        this.whiteList5minutes = whiteList5minutes;
        this.routeProperties = routeProperties;
    }

    @GetMapping("/index")
    public String index(){
        return JSON.toJSONString(routeProperties);
    }

    @GetMapping("/redis/{key}")
    public Mono<Object> redis(@PathVariable("key") String key){
        return whiteList5minutes.get(key)
                .cast(Object.class)
                .switchIfEmpty(Mono.just("empty"));
    }

    @GetMapping("/test/ipSetLast10Seconds")
    public Mono<Boolean> ipSetLast10Seconds(){
        String preIp = "59.82.21.";
        List<Mono<Boolean>> setOperations = Lists.newArrayList();
        for (int i = 0; i < 155; i++) {
            String ip = preIp+i;
            String formatDateTime = DateUtil.formatDateTime(DateUtil.date());
            setOperations.add(ipSetLast10Seconds.set(ip, formatDateTime));
        }
        return Mono.when(setOperations)
                .then(Mono.just(true));
    }

    @GetMapping("/test/ipSetLast10SecondsError")
    public Mono<Boolean> ipSetLast10SecondsError(){
        String mockIp = "59.82.21.100";
        String formatDateTime = DateUtil.formatDateTime(DateUtil.date());
        return ipSetLast10Seconds.set(mockIp, formatDateTime);
    }


}