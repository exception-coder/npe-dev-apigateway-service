package com.dev.gateway.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import java.util.Arrays;
import java.util.List;

/**
 * IP地址获取工具类
 * 统一处理各种场景下的客户端IP地址获取逻辑
 * 
 * 支持的场景：
 * 1. 代理环境下的真实IP获取（X-Forwarded-For, X-Real-IP）
 * 2. IPv6到IPv4的转换
 * 3. 测试环境的Mock IP支持
 * 4. 本地开发环境的IP处理
 * 
 * @author 系统
 * @version 1.0
 */
@Slf4j
public class IpUtil {

    /**
     * 常见的代理头部字段，按优先级排序
     */
    private static final List<String> PROXY_HEADERS = Arrays.asList(
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR");

    /**
     * 本地回环地址列表
     */
    private static final List<String> LOCAL_ADDRESSES = Arrays.asList(
            "127.0.0.1",
            "localhost",
            "0:0:0:0:0:0:0:1",
            "::1");

    /**
     * 从ServerWebExchange获取客户端IP（使用XForwardedRemoteAddressResolver）
     * 
     * @param exchange ServerWebExchange对象
     * @param resolver XForwardedRemoteAddressResolver实例
     * @return 客户端IP地址
     */
    public static String getClientIp(ServerWebExchange exchange, XForwardedRemoteAddressResolver resolver) {
        if (exchange == null || resolver == null) {
            return getDefaultLocalIp();
        }

        try {
            // 首先检查是否有Mock IP（用于测试）
            String mockIp = getMockIp(exchange.getRequest());
            if (StringUtils.hasText(mockIp)) {
                return normalizeIp(mockIp);
            }

            // 使用XForwardedRemoteAddressResolver获取IP
            String realIp = resolver.resolve(exchange).getAddress().getHostAddress();
            String path = exchange.getRequest().getURI().getPath();
            log.info("请求路径: {}, 使用XForwardedRemoteAddressResolver获取到IP: {}", path,realIp);
            return normalizeIp(realIp);
        } catch (Exception e) {
            log.warn("使用XForwardedRemoteAddressResolver获取IP失败: {}", e.getMessage());
            // 降级到手动解析
            return getClientIpFromHeaders(exchange.getRequest());
        }
    }

    /**
     * 从ServerHttpRequest获取客户端IP（手动解析头部）
     * 
     * @param request ServerHttpRequest对象
     * @return 客户端IP地址
     */
    public static String getClientIpFromHeaders(ServerHttpRequest request) {
        if (request == null) {
            return getDefaultLocalIp();
        }

        // 首先检查是否有Mock IP（用于测试）
        String mockIp = getMockIp(request);
        if (StringUtils.hasText(mockIp)) {
            return normalizeIp(mockIp);
        }

        // 按优先级检查各种代理头部
        for (String header : PROXY_HEADERS) {
            String ip = request.getHeaders().getFirst(header);
            if (StringUtils.hasText(ip)) {
                // X-Forwarded-For可能包含多个IP，取第一个
                String firstIp = ip.split(",")[0].trim();
                if (isValidIp(firstIp)) {
                    return normalizeIp(firstIp);
                }
            }
        }

        // 最后从连接信息获取
        if (request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
            String ip = request.getRemoteAddress().getAddress().getHostAddress();
            return normalizeIp(ip);
        }

        return getDefaultLocalIp();
    }

    /**
     * 获取Mock IP（用于测试环境）
     * 
     * @param request ServerHttpRequest对象
     * @return Mock IP地址，如果不存在则返回null
     */
    private static String getMockIp(ServerHttpRequest request) {
        return request.getHeaders().getFirst("Mock-IP");
    }

    /**
     * 标准化IP地址格式
     * 主要处理IPv6到IPv4的转换
     * 
     * @param ip 原始IP地址
     * @return 标准化后的IP地址
     */
    public static String normalizeIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return getDefaultLocalIp();
        }

        // 处理IPv6本地回环地址
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return "127.0.0.1";
        }

        // 处理IPv6地址中的IPv4映射
        if (ip.contains("::ffff:") && ip.length() > 7) {
            String ipv4Part = ip.substring(ip.lastIndexOf(':') + 1);
            if (isValidIpv4(ipv4Part)) {
                return ipv4Part;
            }
        }

        return ip;
    }

    /**
     * 检查是否为有效的IP地址
     * 
     * @param ip IP地址字符串
     * @return 是否为有效IP
     */
    public static boolean isValidIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return false;
        }

        // 检查是否为未知或无效值
        if ("unknown".equalsIgnoreCase(ip) || "null".equalsIgnoreCase(ip)) {
            return false;
        }

        // 检查IPv4格式
        if (isValidIpv4(ip)) {
            return true;
        }

        // 检查IPv6格式（简单检查）
        return isValidIpv6(ip);
    }

    /**
     * 检查是否为有效的IPv4地址
     * 
     * @param ip IP地址字符串
     * @return 是否为有效IPv4
     */
    public static boolean isValidIpv4(String ip) {
        if (!StringUtils.hasText(ip)) {
            return false;
        }

        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }

        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 检查是否为有效的IPv6地址（简单检查）
     * 
     * @param ip IP地址字符串
     * @return 是否为有效IPv6
     */
    public static boolean isValidIpv6(String ip) {
        if (!StringUtils.hasText(ip)) {
            return false;
        }

        // 简单的IPv6格式检查
        return ip.contains(":") && ip.length() >= 2;
    }

    /**
     * 检查是否为本地地址
     * 
     * @param ip IP地址
     * @return 是否为本地地址
     */
    public static boolean isLocalAddress(String ip) {
        return LOCAL_ADDRESSES.contains(ip);
    }

    /**
     * 检查是否为私有网络地址
     * 
     * @param ip IP地址
     * @return 是否为私有网络地址
     */
    public static boolean isPrivateNetwork(String ip) {
        if (!isValidIpv4(ip)) {
            return false;
        }

        String[] parts = ip.split("\\.");
        int firstOctet = Integer.parseInt(parts[0]);
        int secondOctet = Integer.parseInt(parts[1]);

        // 10.0.0.0/8
        if (firstOctet == 10) {
            return true;
        }

        // 172.16.0.0/12
        if (firstOctet == 172 && secondOctet >= 16 && secondOctet <= 31) {
            return true;
        }

        // 192.168.0.0/16
        if (firstOctet == 192 && secondOctet == 168) {
            return true;
        }

        return false;
    }

    /**
     * 获取默认的本地IP地址
     * 
     * @return 默认本地IP
     */
    public static String getDefaultLocalIp() {
        return "127.0.0.1";
    }

    /**
     * 格式化IP地址用于日志输出
     * 
     * @param ip IP地址
     * @return 格式化后的IP地址
     */
    public static String formatIpForLog(String ip) {
        if (!StringUtils.hasText(ip)) {
            return "unknown";
        }

        // 对于私有网络地址，可以完整显示
        if (isPrivateNetwork(ip) || isLocalAddress(ip)) {
            return ip;
        }

        // 对于公网IP，可以考虑脱敏处理（根据需要）
        return ip;
    }

    /**
     * 获取IP地址的地理位置类型
     * 
     * @param ip IP地址
     * @return 地理位置类型描述
     */
    public static String getIpLocationType(String ip) {
        if (!StringUtils.hasText(ip)) {
            return "unknown";
        }

        if (isLocalAddress(ip)) {
            return "local";
        }

        if (isPrivateNetwork(ip)) {
            return "private";
        }

        return "public";
    }
}