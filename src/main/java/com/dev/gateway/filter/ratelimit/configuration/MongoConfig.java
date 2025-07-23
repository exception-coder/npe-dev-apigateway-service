package com.dev.gateway.filter.ratelimit.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * MongoDB配置类
 * 配置MongoDB相关的Bean和异步支持
 *
 * @author 系统
 * @version 1.0
 */
@Configuration
@EnableAsync
public class MongoConfig {

    /**
     * 配置ObjectMapper，支持时间序列化
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}