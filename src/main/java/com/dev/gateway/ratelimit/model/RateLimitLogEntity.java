package com.dev.gateway.ratelimit.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 限流日志实体类
 * 用于记录API网关的限流触发情况到MongoDB
 *
 * @author 系统
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "rate_limit_logs")
public class RateLimitLogEntity {

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
     * 限流类型
     * SLIDING_WINDOW_IP_PATH: 滑动窗口IP+路径限流
     * CAPTCHA_REQUIRED: 验证码触发限流
     * IP_RATE_LIMIT: IP限流
     * DDOS_PROTECTION: DDoS防护
     * DDOS_THRESHOLD: DDoS阈值触发
     */
    @Indexed
    private String rateLimitType;

    /**
     * 限流原因描述
     */
    private String limitReason;

    /**
     * 是否在白名单中
     */
    @Indexed
    private Boolean inWhiteList;

    /**
     * 用户代理信息
     */
    private String userAgent;

    /**
     * 请求头信息（JSON格式）
     */
    private String requestHeaders;

    /**
     * 当前窗口内的请求计数
     */
    private Integer currentRequestCount;

    /**
     * 限流阈值
     */
    private Integer limitThreshold;

    /**
     * 限流窗口大小（秒）
     */
    private Integer windowSizeSeconds;

    /**
     * 触发时间
     */
    @Indexed(expireAfterSeconds = 2592000) // 30天后自动删除
    private LocalDateTime triggerTime;

    /**
     * 响应状态码
     */
    private Integer responseStatus;

    /**
     * 是否为DDoS攻击
     */
    @Indexed
    private Boolean isDdosAttack;

    /**
     * 当前活跃IP数量（用于DDoS检测）
     */
    private Integer activeIpCount;

    /**
     * 处理耗时（毫秒）
     */
    private Long processingTimeMs;

    /**
     * 附加元数据信息
     */
    private String metadata;

    /**
     * 创建时间
     */
    @Indexed
    private LocalDateTime createTime;

    /**
     * 地理位置信息
     */
    private String geoLocation;

    /**
     * 请求ID（用于追踪）
     */
    @Indexed
    private String requestId;
}