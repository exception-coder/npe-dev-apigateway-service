package com.dev.gateway.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;

/**
 * 网关路由配置属性
 * 配置网关代理的目标服务端点
 */
@RefreshScope
@Configuration
@ConfigurationProperties(prefix = "gateway.route")
@Data
@Validated
public class RouteProperties {
    /**
     * Pure Admin Service端点
     * 用于处理 /pure-admin-service/** 路径的请求
     */
    @NotBlank(message = "Pure Admin Service端点不能为空")
    private String pureAdminServiceEndpoint = "lb://pure-admin-service";
}
