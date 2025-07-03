package com.dev.gateway.access.context;

public final class AccessRecordContextKeys {

    private AccessRecordContextKeys() {
    }


    public static final String RATE_LIMITED = "access.context.rateLimited";

    public static final String RATE_LIMIT_TYPE = "access.context.rateLimitType";

    public static final String WHITELIST5_MINUTES_FLATMAP = "access.context.whiteList5minutesFlatMap";

    public static final String REQUEST_START_TIME = "access.context.requestStartTime";

    public static final String ACCESS_RECORD_ID = "access.context.accessRecordId";

    public static final String RESPONSE_BODY = "access.context.responseBody";

    public static final String ACCESS = "access.context.access";

    // 也可以根据对象字段自动生成 key，比如：
    public static String keyOf(String fieldName) {
        return "access.context." + fieldName;
    }
}