package com.dev.gateway.service.redis;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

public class ExpiringValueService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    private final String preKeyName;

    private final Duration ttl;

    public ExpiringValueService(ReactiveRedisTemplate<String, String> redisTemplate,String preKeyName,Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.preKeyName = preKeyName;
        this.ttl = ttl;
    }

    public Mono<Boolean> set(String keyName,String value) {
        return redisTemplate.opsForValue().set(this.preKeyName+":"+keyName, value,this.ttl);
//                .then(redisTemplate.expire(this.preKeyName+":"+keyName, this.ttl));
    }

    public Mono<String> get(String keyName) {
        return redisTemplate.opsForValue().get(this.preKeyName+":"+keyName);
    }

    public Flux<String> getKeysWithPrefix() {
        ScanOptions options = ScanOptions.scanOptions()
                .match(this.preKeyName + "*") // 设置匹配模式
                .count(100) // 设置每次返回的键数量
                .build();
        return redisTemplate.scan(options)
                .filter(key -> key.startsWith(preKeyName+":"));
    }

}