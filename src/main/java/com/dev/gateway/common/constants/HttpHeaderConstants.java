package com.dev.gateway.common.constants;

/**
 * HTTP头常量类
 * 统一管理项目中使用的HTTP头名称常量，避免魔法值
 * 
 * @author 系统
 * @version 1.0
 */
public final class HttpHeaderConstants {

    private HttpHeaderConstants() {
        // 工具类，禁止实例化
    }

    // ================= 标准HTTP头 =================

    /**
     * 用户代理头
     */
    public static final String USER_AGENT = "User-Agent";

    /**
     * 内容类型头
     */
    public static final String CONTENT_TYPE = "Content-Type";

    /**
     * 接受头
     */
    public static final String ACCEPT = "Accept";

    /**
     * 接受语言头
     */
    public static final String ACCEPT_LANGUAGE = "Accept-Language";

    /**
     * 接受编码头
     */
    public static final String ACCEPT_ENCODING = "Accept-Encoding";

    /**
     * 连接类型头
     */
    public static final String CONNECTION = "Connection";

    /**
     * 引用来源头
     */
    public static final String REFERER = "Referer";

    // ================= 代理和转发相关头 =================

    /**
     * 转发的客户端IP头
     */
    public static final String X_FORWARDED_FOR = "X-Forwarded-For";

    /**
     * 真实IP头
     */
    public static final String X_REAL_IP = "X-Real-IP";

    /**
     * 转发的协议头
     */
    public static final String X_FORWARDED_PROTO = "X-Forwarded-Proto";

    // ================= 安全相关头 =================

    /**
     * XSS保护头
     */
    public static final String X_XSS_PROTECTION = "X-XSS-Protection";

    /**
     * 防止点击劫持头
     */
    public static final String X_FRAME_OPTIONS = "X-Frame-Options";

    /**
     * 防止MIME类型嗅探头
     */
    public static final String X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";

    /**
     * 引用策略头
     */
    public static final String REFERRER_POLICY = "Referrer-Policy";

    /**
     * 内容安全策略头
     */
    public static final String CONTENT_SECURITY_POLICY = "Content-Security-Policy";

    // ================= 自定义响应头 =================

    /**
     * 重定向URL头（限流时使用）
     */
    public static final String REDIRECT_URL = "redirectUrl";

    /**
     * 链路追踪ID头
     */
    public static final String X_TRACE_ID = "x-trace-id";

    /**
     * 浏览器指纹头
     */
    public static final String X_BROWSER_FINGERPRINT = "x-browser-fingerprint";

    /**
     * Mock IP头（测试用）
     */
    public static final String MOCK_IP = "Mock-IP";

    // ================= AJAX相关头 =================

    /**
     * XMLHttpRequest标识头
     */
    public static final String X_REQUESTED_WITH = "X-Requested-With";

    // ================= 常用值常量 =================

    /**
     * XMLHttpRequest值
     */
    public static final String XML_HTTP_REQUEST = "XMLHttpRequest";

    /**
     * Keep-Alive连接值
     */
    public static final String KEEP_ALIVE = "keep-alive";

    /**
     * JSON内容类型值
     */
    public static final String APPLICATION_JSON = "application/json";

    /**
     * 表单内容类型值
     */
    public static final String APPLICATION_FORM_URLENCODED = "application/x-www-form-urlencoded";
}