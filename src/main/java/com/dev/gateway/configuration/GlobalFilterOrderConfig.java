package com.dev.gateway.configuration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 全局过滤器执行顺序统一管理
 * 用于统一编排所有全局过滤器的执行顺序，避免在每个过滤器中分散定义
 * 使用有序集合自动分配order值，支持动态调整顺序而无需手动修改其他过滤器的order
 * 
 * @author 系统
 * @version 2.0
 */
public final class GlobalFilterOrderConfig {

    // ================= 过滤器顺序定义 =================

    /**
     * 过滤器执行顺序配置
     * 使用LinkedHashMap保证插入顺序，自动分配递增的order值
     */
    private static final Map<String, FilterInfo> FILTER_ORDER_MAP = new LinkedHashMap<>();

    /**
     * 过滤器信息
     */
    public static class FilterInfo {
        private final String name;
        private final String description;
        private final int order;

        public FilterInfo(String name, String description, int order) {
            this.name = name;
            this.description = description;
            this.order = order;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public int getOrder() {
            return order;
        }
    }

    // ================= 初始化过滤器顺序 =================

    static {
        AtomicInteger orderCounter = new AtomicInteger(Integer.MIN_VALUE);

        // 按执行顺序依次添加过滤器
        addFilter("TRACE_LOGGING", "链路追踪过滤器 - 第四优先级，负责初始化SkyWalking追踪上下文", orderCounter);
        addFilter("DDOS_RATE_LIMIT", "DDoS防护过滤器 - 优先级最高，负责DDoS攻击检测和防护", orderCounter);
        addFilter("BROWSER_DETECTION", "浏览器检测过滤器 - 第二优先级，负责检测和拦截非真实浏览器请求", orderCounter);
        addFilter("API_RATE_LIMIT", "API限流过滤器 - 第三优先级，负责IP限流、验证码机制、白名单检查等核心安全功能", orderCounter);

        // 中等优先级过滤器，间隔设置为10
        orderCounter.set(Integer.MIN_VALUE + 10);
        addFilter("REQUEST_LOGGER", "请求日志过滤器 - 中等优先级，负责记录和处理请求体内容", orderCounter);

        // 较低优先级过滤器，间隔设置为20
        orderCounter.set(Integer.MIN_VALUE + 20);
        addFilter("ACCESS_LOGGING", "访问日志过滤器 - 较低优先级，负责记录访问信息和响应时间", orderCounter);

        // 最低优先级过滤器
        orderCounter.set(Integer.MAX_VALUE - 1000);
        addFilter("ACCESS_RECORDING", "访问记录过滤器 - 最低优先级，负责记录访问信息到MongoDB，在所有业务处理完成后执行", orderCounter);
    }

    /**
     * 添加过滤器到顺序配置中
     */
    private static void addFilter(String name, String description, AtomicInteger orderCounter) {
        int order = orderCounter.getAndIncrement();
        FILTER_ORDER_MAP.put(name, new FilterInfo(name, description, order));
    }

    // ================= 过滤器执行顺序常量 =================

    /**
     * DDoS防护过滤器
     */
    public static final int DDOS_RATE_LIMIT_FILTER_ORDER = getFilterOrder("DDOS_RATE_LIMIT");

    /**
     * 浏览器检测过滤器
     */
    public static final int BROWSER_DETECTION_FILTER_ORDER = getFilterOrder("BROWSER_DETECTION");

    /**
     * API限流过滤器
     */
    public static final int API_RATE_LIMIT_FILTER_ORDER = getFilterOrder("API_RATE_LIMIT");

    /**
     * 链路追踪过滤器
     */
    public static final int TRACE_LOGGING_FILTER_ORDER = getFilterOrder("TRACE_LOGGING");

    /**
     * 请求日志过滤器
     */
    public static final int REQUEST_LOGGER_FILTER_ORDER = getFilterOrder("REQUEST_LOGGER");

    /**
     * 访问日志过滤器
     */
    public static final int ACCESS_LOGGING_FILTER_ORDER = getFilterOrder("ACCESS_LOGGING");

    /**
     * 访问记录过滤器
     */
    public static final int ACCESS_RECORDING_FILTER_ORDER = getFilterOrder("ACCESS_RECORDING");

    // ================= 工具方法 =================

    /**
     * 获取指定过滤器的执行顺序
     */
    public static int getFilterOrder(String filterName) {
        FilterInfo filterInfo = FILTER_ORDER_MAP.get(filterName);
        if (filterInfo == null) {
            throw new IllegalArgumentException("未找到过滤器: " + filterName);
        }
        return filterInfo.getOrder();
    }

    /**
     * 获取所有过滤器信息
     */
    public static Map<String, FilterInfo> getAllFilters() {
        return new LinkedHashMap<>(FILTER_ORDER_MAP);
    }

    /**
     * 打印过滤器执行顺序
     */
    public static void printFilterOrder() {
        System.out.println("=== 全局过滤器执行顺序 ===");
        FILTER_ORDER_MAP.forEach((name, info) -> {
            System.out.printf("Order: %d, Name: %s, Description: %s%n",
                    info.getOrder(), info.getName(), info.getDescription());
        });
    }

    // ================= 构造函数私有化 =================

    private GlobalFilterOrderConfig() {
        // 工具类，禁止实例化
    }
}