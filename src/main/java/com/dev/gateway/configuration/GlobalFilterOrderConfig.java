package com.dev.gateway.configuration;

/**
 * 全局过滤器执行顺序统一管理
 * 用于统一编排所有全局过滤器的执行顺序，避免在每个过滤器中分散定义
 * 
 * @author 系统
 * @version 1.0
 */
public final class GlobalFilterOrderConfig {

    // ================= 过滤器执行顺序常量 =================
    
    /**
     * DDoS防护过滤器
     * 优先级最高，负责DDoS攻击检测和防护
     */
    public static final int DDOS_RATE_LIMIT_FILTER_ORDER = Integer.MIN_VALUE;
    
    /**
     * 浏览器检测过滤器
     * 第二优先级，负责检测和拦截非真实浏览器请求
     */
    public static final int BROWSER_DETECTION_FILTER_ORDER = Integer.MIN_VALUE + 1;
    
    /**
     * API限流过滤器  
     * 第三优先级，负责IP限流、验证码机制、白名单检查等核心安全功能
     */
    public static final int API_RATE_LIMIT_FILTER_ORDER = Integer.MIN_VALUE + 2;
    
    /**
     * 链路追踪过滤器
     * 第四优先级，负责初始化SkyWalking追踪上下文
     */
    public static final int TRACE_LOGGING_FILTER_ORDER = Integer.MIN_VALUE + 3;
    
    /**
     * 请求日志过滤器
     * 中等优先级，负责记录和处理请求体内容
     */
    public static final int REQUEST_LOGGER_FILTER_ORDER = Integer.MIN_VALUE + 10;
    
    /**
     * 访问日志过滤器
     * 较低优先级，负责记录访问信息和响应时间
     */
    public static final int ACCESS_LOGGING_FILTER_ORDER = Integer.MIN_VALUE + 20;
    
    /**
     * 访问记录过滤器
     * 最低优先级，负责记录访问信息到MongoDB，在所有业务处理完成后执行
     */
    public static final int ACCESS_RECORDING_FILTER_ORDER = Integer.MAX_VALUE - 1000;

    // ================= 构造函数私有化 =================
    
    private GlobalFilterOrderConfig() {
        // 工具类，禁止实例化
    }
    
    // ================= 过滤器顺序说明 =================
    
         /**
      * 过滤器执行顺序说明：
      * 
      * 1. DdosRateLimitGlobalFilter (Integer.MIN_VALUE)  
      *    - 优先级最高，DDoS攻击防护
      *    - 提供最基础的攻击检测和防护
      *    - 在所有其他处理之前进行攻击检测
      * 
      * 2. BrowserDetectionGlobalFilter (Integer.MIN_VALUE + 1)
      *    - 第二优先级，浏览器真实性检测
      *    - 过滤爬虫和机器人请求
      *    - 在攻击防护之后，其他安全检查之前执行
      * 
      * 3. ApiRateLimitGlobalFilter (Integer.MIN_VALUE + 2)
      *    - 第三优先级，核心安全过滤器
      *    - 处理IP限流、验证码、白名单等关键安全功能
      *    - 在基础防护之后进行详细安全检查
      * 
      * 4. TraceLoggingFilter (Integer.MIN_VALUE + 3)
      *    - 第四优先级，初始化链路追踪上下文
      *    - 为后续所有过滤器提供追踪能力
      * 
      * 5. RequestLoggerGlobalFilter (Integer.MIN_VALUE + 10)
      *    - 请求体日志记录，用于调试和审计
      *    - 在安全检查通过后记录请求详情
      * 
      * 6. AccessLoggingGlobalFilter (Integer.MIN_VALUE + 20)
      *    - 访问日志记录，统计响应时间等信息  
      *    - 在请求处理完成后记录访问统计
      * 
      * 7. AccessRecordingFilter (Integer.MAX_VALUE - 1000)
      *    - 访问记录持久化到MongoDB
      *    - 最后执行，确保所有信息都已收集完整
      */
} 