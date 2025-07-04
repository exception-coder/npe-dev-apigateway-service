package com.dev.gateway.access.context;

/**
 * 访问记录上下文键值常量类
 * 统一管理ServerWebExchange.getAttributes()中使用的键值
 * 
 * @author 系统
 * @version 1.0
 */
public final class AccessRecordContextKeys {

    private AccessRecordContextKeys() {
    }

    // ================= 限流相关 =================

    /**
     * 是否被限流
     */
    public static final String RATE_LIMITED = "access.context.rateLimited";

    /**
     * 限流类型
     */
    public static final String RATE_LIMIT_TYPE = "access.context.rateLimitType";

    // ================= 白名单相关 =================

    /**
     * 白名单标识（通用）
     */
    public static final String WHITELIST_FLATMAP = "access.context.whiteListFlatMap";

    // ================= 黑名单相关 =================

    /**
     * 黑名单标识
     */
    public static final String BLACKLIST_FLATMAP = "access.context.blackListFlatMap";

    /**
     * 黑名单信息
     */
    public static final String BLACKLIST_INFO = "access.context.blackListInfo";

    // ================= 请求记录相关 =================

    /**
     * 请求开始时间
     */
    public static final String REQUEST_START_TIME = "access.context.requestStartTime";

    /**
     * 访问记录ID
     */
    public static final String ACCESS_RECORD_ID = "access.context.accessRecordId";

    /**
     * 响应体
     */
    public static final String RESPONSE_BODY = "access.context.responseBody";

    /**
     * 访问信息
     */
    public static final String ACCESS = "access.context.access";

    // ================= 工具方法 =================

    /**
     * 根据字段名生成键值
     * 
     * @param fieldName 字段名
     * @return 完整的键值
     */
    public static String keyOf(String fieldName) {
        return "access.context." + fieldName;
    }
}