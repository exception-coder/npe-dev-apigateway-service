package com.dev.gateway.filter.access.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 访问记录数据模型
 * 存储网关的所有访问记录到MongoDB
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "access_records")
public class AccessRecord {

    /**
     * 主键ID
     */
    @Id
    private String id;

    /**
     * 客户端IP地址
     */
    @Indexed
    private String clientIp;

    /**
     * 请求路径
     */
    @Indexed
    private String requestPath;

    /**
     * HTTP方法
     */
    private String httpMethod;

    /**
     * 用户代理
     */
    private String userAgent;

    /**
     * 请求头信息（JSON格式）
     */
    private String requestHeaders;

    /**
     * 响应状态码
     */
    private Integer responseStatus;

    /**
     * 请求处理时间（毫秒）
     */
    private Long processingTimeMs;

    /**
     * 是否被限流
     */
    @Indexed
    private Boolean rateLimited;

    /**
     * 限流类型（如：minute, second, captcha等）
     */
    private String rateLimitType;

    /**
     * 是否在白名单中
     */
    @Indexed
    private Boolean inWhiteList;

    /**
     * 访问时间
     */
    @Indexed(expireAfterSeconds = 2592000) // 30天后自动删除
    private LocalDateTime accessTime;

    /**
     * 请求ID（用于追踪）
     */
    @Indexed
    private String requestId;

    /**
     * 地理位置信息
     */
    private GeoLocation geoLocation;

    /**
     * 额外的元数据
     */
    private String metadata;

    /**
     * 请求体内容
     */
    private String requestBody;

    /**
     * 响应体内容
     */
    private String responseBody;

    /**
     * 链路追踪ID
     */
    @Indexed
    private String traceId;

    /**
     * 链路跨度ID
     */
    private String spanId;

    /**
     * 地理位置信息内嵌文档
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeoLocation {
        /**
         * 国家
         */
        private String country;

        /**
         * 地区/省份
         */
        private String region;

        /**
         * 城市
         */
        private String city;

        /**
         * 纬度
         */
        private Double latitude;

        /**
         * 经度
         */
        private Double longitude;

        /**
         * ISP信息
         */
        private String isp;
    }
}