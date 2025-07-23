package com.dev.gateway.config;

import com.dev.gateway.service.IpResolverService;
import com.dev.gateway.utils.IpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

/**
 * IP地址解析器配置类
 * 提供统一的IP获取服务配置
 * 
 * @author 系统
 * @version 1.0
 */
@Configuration
@Slf4j
public class IpResolverConfig {

    /**
     * 创建主要的IP解析服务Bean
     * 
     * @param xForwardedRemoteAddressResolver 配置的IP解析器
     * @return IP解析服务实例
     */
    @Bean
    @Primary
    public IpResolverService ipResolverService(
            @Qualifier("rateLimitIpResolver") XForwardedRemoteAddressResolver xForwardedRemoteAddressResolver) {
        return new IpResolverService(xForwardedRemoteAddressResolver);
    }

    /**
     * 提供静态工具方法的包装Bean（可选）
     * 用于需要静态调用的场景
     * 
     * @return IP工具类包装器
     */
    @Bean("ipUtilWrapper")
    public IpUtilWrapper ipUtilWrapper() {
        return new IpUtilWrapper();
    }

    /**
     * IP工具类包装器
     * 提供静态方法的Spring Bean包装
     */
    public static class IpUtilWrapper {

        /**
         * 从ServerWebExchange获取客户端IP
         */
        public String getClientIp(ServerWebExchange exchange, XForwardedRemoteAddressResolver resolver) {
            return IpUtil.getClientIp(exchange, resolver);
        }

        /**
         * 从ServerHttpRequest获取客户端IP
         */
        public String getClientIp(ServerHttpRequest request) {
            return IpUtil.getClientIpFromHeaders(request);
        }

        /**
         * 标准化IP地址
         */
        public String normalizeIp(String ip) {
            return IpUtil.normalizeIp(ip);
        }

        /**
         * 检查IP是否有效
         */
        public boolean isValidIp(String ip) {
            return IpUtil.isValidIp(ip);
        }

        /**
         * 检查是否为IPv4地址
         */
        public boolean isValidIpv4(String ip) {
            return IpUtil.isValidIpv4(ip);
        }

        /**
         * 检查是否为本地地址
         */
        public boolean isLocalAddress(String ip) {
            return IpUtil.isLocalAddress(ip);
        }

        /**
         * 检查是否为私有网络地址
         */
        public boolean isPrivateNetwork(String ip) {
            return IpUtil.isPrivateNetwork(ip);
        }

        /**
         * 格式化IP地址用于日志
         */
        public String formatIpForLog(String ip) {
            return IpUtil.formatIpForLog(ip);
        }

        /**
         * 获取IP地址的地理位置类型
         */
        public String getIpLocationType(String ip) {
            return IpUtil.getIpLocationType(ip);
        }
    }
}