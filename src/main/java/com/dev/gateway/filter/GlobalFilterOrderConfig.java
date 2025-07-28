package com.dev.gateway.filter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 全局过滤器执行顺序统一管理中心
 * 
 * <h3>设计理念</h3>
 * <p>
 * 本配置类统一管理网关所有全局过滤器的执行顺序，确保过滤器按照正确的逻辑顺序执行，
 * 避免在各个过滤器类中分散定义order值，便于整体架构的维护和调整。
 * </p>
 * 
 * <h3>执行顺序设计原则</h3>
 * <ol>
 * <li><strong>链路追踪优先</strong>：最先初始化追踪上下文，为所有后续过滤器提供日志追踪能力</li>
 * <li><strong>安全防护前置</strong>：DDoS防护和浏览器检测等安全过滤器优先执行，及早拦截恶意请求</li>
 * <li><strong>业务逻辑居中</strong>：API限流等业务相关过滤器在安全检查完成后执行</li>
 * <li><strong>日志记录靠后</strong>：请求日志和访问日志在业务处理完成后记录</li>
 * <li><strong>数据持久化最后</strong>：访问记录存储在所有处理完成后异步执行</li>
 * </ol>
 * 
 * <h3>技术特点</h3>
 * <ul>
 * <li>使用LinkedHashMap保证插入顺序</li>
 * <li>自动递增分配order值，避免冲突</li>
 * <li>分组设置优先级间隔，便于后续插入新过滤器</li>
 * <li>集中管理，易于维护和调试</li>
 * </ul>
 * 
 * @author 系统
 * @version 3.0
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

        // === 第一优先级：链路追踪初始化（必须最先执行，为后续过滤器提供追踪上下文） ===
        addFilter("TRACE_LOGGING", "链路追踪过滤器 - 最高优先级，负责初始化SkyWalking追踪上下文和MDC日志上下文", orderCounter);

        // === 第二优先级：DDoS防护（核心安全防护，需要尽早拦截恶意流量） ===
        addFilter("DDOS_RATE_LIMIT", "DDoS防护过滤器 - 核心安全防护，负责DDoS攻击检测、黑白名单管理和验证码机制", orderCounter);

        // === 第三优先级：浏览器检测（身份验证，过滤非真实浏览器请求） ===
        addFilter("BROWSER_DETECTION", "浏览器检测过滤器 - 身份验证，负责检测和拦截自动化工具、爬虫等非真实浏览器请求", orderCounter);

        // === 第四优先级：API限流（业务层限流，更精细的流量控制） ===
        addFilter("API_RATE_LIMIT", "API限流过滤器 - 业务层限流，负责滑动窗口限流、验证码触发和IP+路径级别的精细化流量控制", orderCounter);

        // === 中等优先级：请求日志记录（安全检查完成后记录请求详情） ===
        orderCounter.set(Integer.MIN_VALUE + 50);
        addFilter("REQUEST_LOGGER", "请求日志过滤器 - 中等优先级，负责记录和处理POST/PUT/PATCH请求体内容，支持敏感信息过滤", orderCounter);

        // === 较低优先级：访问日志记录（记录访问信息和性能指标） ===
        orderCounter.set(Integer.MIN_VALUE + 100);
        addFilter("ACCESS_LOGGING", "访问日志过滤器 - 较低优先级，负责记录访问信息、响应时间和性能指标，输出结构化访问日志", orderCounter);

        // === 最低优先级：数据持久化（所有业务处理完成后进行数据存储） ===
        orderCounter.set(Integer.MAX_VALUE - 1000);
        addFilter("ACCESS_RECORDING", "访问记录过滤器 - 最低优先级，负责将完整的访问信息异步存储到MongoDB，用于后续分析和审计", orderCounter);
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