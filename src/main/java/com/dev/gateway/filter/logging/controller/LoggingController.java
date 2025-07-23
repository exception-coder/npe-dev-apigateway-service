package com.dev.gateway.filter.logging.controller;

import com.dev.gateway.filter.logging.properties.LoggingProperties;
import com.dev.gateway.filter.logging.service.LoggingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 日志管理控制器
 * 提供日志配置管理和状态查询功能
 */
@RestController
@RequestMapping("/api/logging")
@Slf4j
public class LoggingController {

    @Autowired
    private LoggingService loggingService;
    
    @Autowired
    private LoggingProperties loggingProperties;

    /**
     * 获取日志配置状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getLoggingStatus() {
        log.info("获取日志配置状态");
        
        Map<String, Object> status = new HashMap<>();
        status.put("accessLogEnabled", loggingProperties.isAccessLogEnabled());
        status.put("requestBodyEnabled", loggingProperties.isRequestBodyEnabled());
        status.put("responseBodyEnabled", loggingProperties.isResponseBodyEnabled());
        status.put("verboseLogging", loggingProperties.isVerboseLogging());
        status.put("logHeaders", loggingProperties.isLogHeaders());
        status.put("logResponseHeaders", loggingProperties.isLogResponseHeaders());
        status.put("maxRequestBodyLength", loggingProperties.getMaxRequestBodyLength());
        status.put("maxResponseBodyLength", loggingProperties.getMaxResponseBodyLength());
        status.put("logLevel", loggingProperties.getLogLevel());
        status.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(status);
    }

    /**
     * 获取日志统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getLoggingStats() {
        log.info("获取日志统计信息");
        
        LoggingService.LoggingStats stats = loggingService.getStats();
        
        Map<String, Object> result = new HashMap<>();
        result.put("accessLogEnabled", stats.isAccessLogEnabled());
        result.put("requestBodyEnabled", stats.isRequestBodyEnabled());
        result.put("responseBodyEnabled", stats.isResponseBodyEnabled());
        result.put("verboseLogging", stats.isVerboseLogging());
        result.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(result);
    }

    /**
     * 管理员接口：动态修改访问日志配置
     */
    @PostMapping("/admin/access-log/{enabled}")
    public ResponseEntity<Map<String, Object>> setAccessLogEnabled(@PathVariable boolean enabled) {
        log.info("管理员修改访问日志配置: {}", enabled);
        
        loggingProperties.setAccessLogEnabled(enabled);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "访问日志配置已更新");
        result.put("accessLogEnabled", enabled);
        
        return ResponseEntity.ok(result);
    }

    /**
     * 管理员接口：动态修改请求体日志配置
     */
    @PostMapping("/admin/request-body-log/{enabled}")
    public ResponseEntity<Map<String, Object>> setRequestBodyLogEnabled(@PathVariable boolean enabled) {
        log.info("管理员修改请求体日志配置: {}", enabled);
        
        loggingProperties.setRequestBodyEnabled(enabled);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "请求体日志配置已更新");
        result.put("requestBodyEnabled", enabled);
        
        return ResponseEntity.ok(result);
    }

    /**
     * 管理员接口：动态修改响应体日志配置
     */
    @PostMapping("/admin/response-body-log/{enabled}")
    public ResponseEntity<Map<String, Object>> setResponseBodyLogEnabled(@PathVariable boolean enabled) {
        log.info("管理员修改响应体日志配置: {}", enabled);
        
        loggingProperties.setResponseBodyEnabled(enabled);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "响应体日志配置已更新");
        result.put("responseBodyEnabled", enabled);
        
        return ResponseEntity.ok(result);
    }

    /**
     * 管理员接口：动态修改详细日志配置
     */
    @PostMapping("/admin/verbose-log/{enabled}")
    public ResponseEntity<Map<String, Object>> setVerboseLogEnabled(@PathVariable boolean enabled) {
        log.info("管理员修改详细日志配置: {}", enabled);
        
        loggingProperties.setVerboseLogging(enabled);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "详细日志配置已更新");
        result.put("verboseLogging", enabled);
        
        return ResponseEntity.ok(result);
    }

    /**
     * 管理员接口：批量更新日志配置
     */
    @PostMapping("/admin/config")
    public ResponseEntity<Map<String, Object>> updateLoggingConfig(@RequestBody Map<String, Object> config) {
        log.info("管理员批量更新日志配置: {}", config);
        
        try {
            if (config.containsKey("accessLogEnabled")) {
                loggingProperties.setAccessLogEnabled((Boolean) config.get("accessLogEnabled"));
            }
            if (config.containsKey("requestBodyEnabled")) {
                loggingProperties.setRequestBodyEnabled((Boolean) config.get("requestBodyEnabled"));
            }
            if (config.containsKey("responseBodyEnabled")) {
                loggingProperties.setResponseBodyEnabled((Boolean) config.get("responseBodyEnabled"));
            }
            if (config.containsKey("verboseLogging")) {
                loggingProperties.setVerboseLogging((Boolean) config.get("verboseLogging"));
            }
            if (config.containsKey("logHeaders")) {
                loggingProperties.setLogHeaders((Boolean) config.get("logHeaders"));
            }
            if (config.containsKey("logResponseHeaders")) {
                loggingProperties.setLogResponseHeaders((Boolean) config.get("logResponseHeaders"));
            }
            if (config.containsKey("maxRequestBodyLength")) {
                loggingProperties.setMaxRequestBodyLength((Integer) config.get("maxRequestBodyLength"));
            }
            if (config.containsKey("maxResponseBodyLength")) {
                loggingProperties.setMaxResponseBodyLength((Integer) config.get("maxResponseBodyLength"));
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "日志配置批量更新成功");
            result.put("updatedConfig", config);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("批量更新日志配置失败", e);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "配置更新失败: " + e.getMessage());
            
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 测试日志记录接口
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testLogging(@RequestBody(required = false) Map<String, Object> requestBody) {
        log.info("测试日志记录接口被调用");
        
        Map<String, Object> result = new HashMap<>();
        result.put("message", "测试日志记录成功");
        result.put("timestamp", System.currentTimeMillis());
        result.put("requestData", requestBody);
        
        return ResponseEntity.ok(result);
    }
} 