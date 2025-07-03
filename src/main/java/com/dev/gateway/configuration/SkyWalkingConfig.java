package com.dev.gateway.configuration;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.toolkit.trace.TraceContext;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SkyWalking 链路追踪配置
 * 
 * @author 系统
 * @version 1.0
 */
@Slf4j
@Configuration
public class SkyWalkingConfig {

    /**
     * SkyWalking 初始化检查
     */
    @Bean
    public ApplicationRunner skyWalkingInitRunner() {
        return args -> {
            try {
                // 检查SkyWalking是否正常工作
                String traceId = TraceContext.traceId();
                if (traceId != null && !traceId.isEmpty()) {
                    log.info("SkyWalking 链路追踪初始化成功，当前TraceId: {}", traceId);
                } else {
                    log.warn("SkyWalking 链路追踪可能未正确启动，TraceId为空");
                }
            } catch (Exception e) {
                log.error("SkyWalking 链路追踪初始化检查失败: {}", e.getMessage());
            }
        };
    }
} 