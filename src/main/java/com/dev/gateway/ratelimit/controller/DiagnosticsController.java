package com.dev.gateway.ratelimit.controller;

import com.dev.gateway.ratelimit.diagnosis.RedisConnectionDiagnostics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 诊断控制器
 * 提供Redis连接和限流相关的诊断接口
 */
@Slf4j
@RestController
@RequestMapping("/internal/diagnostics")
public class DiagnosticsController {

    private final RedisConnectionDiagnostics redisDiagnostics;

    public DiagnosticsController(RedisConnectionDiagnostics redisDiagnostics) {
        this.redisDiagnostics = redisDiagnostics;
    }

    /**
     * 执行完整的Redis诊断
     */
    @PostMapping("/redis/full")
    public Mono<Map<String, Object>> fullRedisDiagnosis() {
        return Mono.fromCallable(() -> {
            log.info("🔧 收到Redis完整诊断请求");
            
            redisDiagnostics.performDiagnosis();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Redis完整诊断已完成，请查看日志");
            result.put("timestamp", System.currentTimeMillis());
            
            return result;
        });
    }

    /**
     * 执行快速Redis诊断
     */
    @PostMapping("/redis/quick")
    public Mono<Map<String, Object>> quickRedisDiagnosis() {
        return Mono.fromCallable(() -> {
            log.info("🚀 收到Redis快速诊断请求");
            
            redisDiagnostics.quickDiagnosis();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Redis快速诊断已完成，请查看日志");
            result.put("timestamp", System.currentTimeMillis());
            
            return result;
        });
    }

    /**
     * 获取系统状态信息
     */
    @GetMapping("/system/status")
    public Mono<Map<String, Object>> getSystemStatus() {
        return Mono.fromCallable(() -> {
            Map<String, Object> status = new HashMap<>();
            
            // JVM信息
            Runtime runtime = Runtime.getRuntime();
            Map<String, Object> jvm = new HashMap<>();
            jvm.put("totalMemory", runtime.totalMemory() / 1024 / 1024 + "MB");
            jvm.put("freeMemory", runtime.freeMemory() / 1024 / 1024 + "MB");
            jvm.put("maxMemory", runtime.maxMemory() / 1024 / 1024 + "MB");
            jvm.put("processors", runtime.availableProcessors());
            
            // 线程信息
            ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
            ThreadGroup parentGroup;
            while ((parentGroup = rootGroup.getParent()) != null) {
                rootGroup = parentGroup;
            }
            
            Map<String, Object> threads = new HashMap<>();
            threads.put("activeCount", Thread.activeCount());
            threads.put("rootGroupActiveCount", rootGroup.activeCount());
            
            status.put("jvm", jvm);
            status.put("threads", threads);
            status.put("timestamp", System.currentTimeMillis());
            
            return status;
        });
    }
} 