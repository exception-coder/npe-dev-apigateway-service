package com.dev.gateway.service;

import com.dev.gateway.utils.IpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

/**
 * IP地址解析服务
 * 提供统一的IP获取接口，支持依赖注入
 * 
 * 主要功能：
 * 1. 统一的IP获取接口
 * 2. 支持不同的解析策略
 * 3. 提供缓存和性能优化
 * 4. 统一的错误处理和日志记录
 * 
 * @author 系统
 * @version 1.0
 */
@Slf4j
@Service
public class IpResolverService {

    private final XForwardedRemoteAddressResolver xForwardedRemoteAddressResolver;

    /**
     * 构造函数注入XForwardedRemoteAddressResolver
     * 
     * @param xForwardedRemoteAddressResolver IP解析器
     */
    public IpResolverService(
            @Qualifier("rateLimitIpResolver") XForwardedRemoteAddressResolver xForwardedRemoteAddressResolver) {
        this.xForwardedRemoteAddressResolver = xForwardedRemoteAddressResolver;
    }

    /**
     * 从ServerWebExchange获取客户端IP地址
     * 这是推荐的获取方式，使用配置的XForwardedRemoteAddressResolver
     * 
     * @param exchange ServerWebExchange对象
     * @return 客户端IP地址
     */
    public String getClientIp(ServerWebExchange exchange) {
        return IpUtil.getClientIp(exchange, xForwardedRemoteAddressResolver);
    }

    /**
     * 从ServerHttpRequest获取客户端IP地址
     * 当无法使用ServerWebExchange时的备用方案
     * 
     * @param request ServerHttpRequest对象
     * @return 客户端IP地址
     */
    public String getClientIp(ServerHttpRequest request) {
        return IpUtil.getClientIpFromHeaders(request);
    }

    /**
     * 获取并标准化IP地址
     * 
     * @param exchange ServerWebExchange对象
     * @return 标准化后的IP地址
     */
    public String getAndNormalizeClientIp(ServerWebExchange exchange) {
        String ip = getClientIp(exchange);
        return IpUtil.normalizeIp(ip);
    }

    /**
     * 获取IP地址的详细信息
     * 
     * @param exchange ServerWebExchange对象
     * @return IP地址详细信息
     */
    public IpInfo getClientIpInfo(ServerWebExchange exchange) {
        String ip = getClientIp(exchange);
        return new IpInfo(ip);
    }

    /**
     * 检查IP地址是否有效
     * 
     * @param ip IP地址字符串
     * @return 是否有效
     */
    public boolean isValidIp(String ip) {
        return IpUtil.isValidIp(ip);
    }

    /**
     * 检查是否为本地地址
     * 
     * @param ip IP地址
     * @return 是否为本地地址
     */
    public boolean isLocalAddress(String ip) {
        return IpUtil.isLocalAddress(ip);
    }

    /**
     * 检查是否为私有网络地址
     * 
     * @param ip IP地址
     * @return 是否为私有网络地址
     */
    public boolean isPrivateNetwork(String ip) {
        return IpUtil.isPrivateNetwork(ip);
    }

    /**
     * IP地址信息类
     */
    public static class IpInfo {
        private final String ip;
        private final String normalizedIp;
        private final boolean isValid;
        private final boolean isLocal;
        private final boolean isPrivate;
        private final boolean isIpv4;
        private final boolean isIpv6;
        private final String locationType;

        public IpInfo(String ip) {
            this.ip = ip;
            this.normalizedIp = IpUtil.normalizeIp(ip);
            this.isValid = IpUtil.isValidIp(ip);
            this.isLocal = IpUtil.isLocalAddress(ip);
            this.isPrivate = IpUtil.isPrivateNetwork(ip);
            this.isIpv4 = IpUtil.isValidIpv4(ip);
            this.isIpv6 = IpUtil.isValidIpv6(ip);
            this.locationType = IpUtil.getIpLocationType(ip);
        }

        public String getIp() {
            return ip;
        }

        public String getNormalizedIp() {
            return normalizedIp;
        }

        public boolean isValid() {
            return isValid;
        }

        public boolean isLocal() {
            return isLocal;
        }

        public boolean isPrivate() {
            return isPrivate;
        }

        public boolean isIpv4() {
            return isIpv4;
        }

        public boolean isIpv6() {
            return isIpv6;
        }

        public String getLocationType() {
            return locationType;
        }

        public String getFormattedForLog() {
            return IpUtil.formatIpForLog(ip);
        }

        @Override
        public String toString() {
            return "IpInfo{" +
                    "ip='" + ip + '\'' +
                    ", normalizedIp='" + normalizedIp + '\'' +
                    ", isValid=" + isValid +
                    ", isLocal=" + isLocal +
                    ", isPrivate=" + isPrivate +
                    ", isIpv4=" + isIpv4 +
                    ", isIpv6=" + isIpv6 +
                    ", locationType='" + locationType + '\'' +
                    '}';
        }
    }
}