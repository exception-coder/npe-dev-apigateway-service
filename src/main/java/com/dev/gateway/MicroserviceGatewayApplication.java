package com.dev.gateway;

import com.dev.gateway.filter.access.context.AccessRecordContextKeys;
import com.dev.gateway.filter.access.service.AccessRecordService;
import com.dev.gateway.filter.XssResponseFilter;
import com.dev.gateway.framework.error.GlobalErrorAttributes;
import com.dev.gateway.filter.logging.filter.ResponseLoggerGatewayFilterFactory;
import com.dev.gateway.properties.GatewayProperties;
import com.dev.gateway.properties.RouteProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.ObjectUtils;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.GZIPInputStream;

@Slf4j
@RefreshScope
@EnableScheduling
@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties({ RouteProperties.class })
@EnableMongoRepositories(basePackages = { "com.dev.gateway.ratelimit.repository", "com.dev.gateway.access.repository" })
public class MicroserviceGatewayApplication {

    private static final String NETTY_ACCESS_LOG_ENABLED_PROPERTY = "reactor.netty.http.server.accessLogEnabled";

    private final GatewayProperties gatewayProperties;

    private final XForwardedRemoteAddressResolver xForwardedRemoteAddressResolver;

    private final RedisRateLimiter ipRedisRateLimiter;

    private final KeyResolver myKeyResolver;

    private final RouteProperties routeProperties;

    private final XssResponseFilter xssResponseFilter;

    private final AccessRecordService accessRecordService;

    public MicroserviceGatewayApplication(GatewayProperties gatewayProperties,
            XForwardedRemoteAddressResolver xForwardedRemoteAddressResolver,
            @Qualifier("ipRedisRateLimiter") RedisRateLimiter ipRedisRateLimiter,
            @Qualifier("myKeyResolver") KeyResolver myKeyResolver,
            RouteProperties routeProperties,
            XssResponseFilter xssResponseFilter, AccessRecordService accessRecordService) {
        this.gatewayProperties = gatewayProperties;
        this.xForwardedRemoteAddressResolver = xForwardedRemoteAddressResolver;
        this.ipRedisRateLimiter = ipRedisRateLimiter;
        this.myKeyResolver = myKeyResolver;
        this.routeProperties = routeProperties;
        this.xssResponseFilter = xssResponseFilter;
        this.accessRecordService = accessRecordService;
    }

    public static void main(String[] args) {
        /* 启用 accesslog */
        enableAccessLog();
        SpringApplication.run(MicroserviceGatewayApplication.class, args);
    }

    /**
     * R3 Public API 代理
     *
     * @param builder
     * @return
     */
    @RefreshScope
    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder,
            ResponseLoggerGatewayFilterFactory responseLoggerGatewayFilterFactory) {
        return builder.routes()
                // pure-admin-service路由配置 - 匹配/pure-admin-service/**路径
                .route("pure-admin-service", r -> r.path("/pure-admin-service/**")
                        .filters(f -> f
//                                .requestRateLimiter(c -> {
//                                    c.setKeyResolver(myKeyResolver);
//                                    c.setRateLimiter(ipRedisRateLimiter);
//                                })
                                .cacheRequestBody(byte[].class)
                                .modifyResponseBody(byte[].class, byte[].class,
                                        (exchange, body) -> Mono.fromCallable(() -> {
                                            // 获取响应头中的Content-Type
                                            MediaType contentType = exchange.getResponse().getHeaders()
                                                    .getContentType();
                                            log.info("响应Content-Type: {}", contentType);

                                            // 检查是否为二进制类型
                                            if (isBinaryContent(contentType)) {
                                                log.info("检测到二进制响应，跳过字符串转换处理");
                                                // 对于二进制响应，记录基本信息但不转换为字符串
                                                exchange.getAttributes().put(AccessRecordContextKeys.RESPONSE_BODY,
                                                        "Binary content (" + body.length + " bytes)");
                                                accessRecordService.updateAccessRecordOnComplete(exchange);
                                                return body;
                                            }

                                            // 对于文本类型的响应，进行字符串转换处理
                                            String content;
                                            log.info("系统默认编码:{}", System.getProperty("file.encoding"));

                                            if(!ObjectUtils.isEmpty(body)){
                                                try {
                                                    // 检查是否是gzip压缩数据
                                                    if (body.length > 2
                                                            && (body[0] & 0xFF) == 0x1F
                                                            && (body[1] & 0xFF) == 0x8B) {
                                                        log.info("检测到gzip压缩数据，进行解压");
                                                        try (GZIPInputStream gzipInputStream = new GZIPInputStream(
                                                                new ByteArrayInputStream(body))) {
                                                            byte[] decompressed = gzipInputStream.readAllBytes();
                                                            content = new String(decompressed, StandardCharsets.UTF_8);
                                                            log.info("gzip解压后的内容: {}", content);
                                                        }
                                                    } else {
                                                        // 如果不是gzip，使用正常的解码方式
                                                        if (contentType != null && contentType.getCharset() != null) {
                                                            content = new String(body, contentType.getCharset());
                                                            log.info("使用Content-Type中指定的编码: {}", contentType.getCharset());
                                                        } else {
                                                            content = new String(body, StandardCharsets.UTF_8);
                                                            log.info("使用默认UTF-8编码");
                                                        }
                                                    }

                                                    // 对于较小的响应体，打印详细调试信息
                                                    if (body.length <= 1024) {
                                                        StringBuilder hexString = new StringBuilder();
                                                        for (byte b : body) {
                                                            hexString.append(String.format("%02X ", b));
                                                        }
                                                        log.debug("响应体原始字节(HEX): {}", hexString.toString());
                                                    }

                                                } catch (Exception e) {
                                                    log.error("响应体解码失败", e);
                                                    content = new String(body, StandardCharsets.ISO_8859_1);
                                                }
                                                log.info("响应内容: {}", content);
                                                // 返回清理后的字节数组
                                                exchange.getAttributes().put(AccessRecordContextKeys.RESPONSE_BODY,
                                                        content);
                                            }
                                            accessRecordService.updateAccessRecordOnComplete(exchange);
                                            return body;
                                        })))
                        .uri(routeProperties.getPureAdminServiceEndpoint()))
                // 主网关路由配置
                .route("my-gateway", r -> r.host("admin.chivepockets.com")
                        .filters(f -> f
                                .cacheRequestBody(String.class)
                                .retry(retryConfig -> {
                                    retryConfig.setBackoff(Duration.ofSeconds(3), Duration.ofSeconds(3), 2, false);
                                    retryConfig.setRetries(3);
                                    retryConfig.setStatuses(HttpStatus.GATEWAY_TIMEOUT,
                                            HttpStatus.INTERNAL_SERVER_ERROR);
                                })
                                .modifyResponseBody(byte[].class, byte[].class,
                                        xssResponseFilter::sanitizeResponseBody))
                        .uri(routeProperties.getPureAdminServiceEndpoint()))
                .build();
    }

    @Bean
    public ErrorAttributes errorAttributes() {
        return new GlobalErrorAttributes();
    }

    private static void enableAccessLog() {
        System.setProperty(NETTY_ACCESS_LOG_ENABLED_PROPERTY, "true");
    }

    /**
     * 检查是否为二进制内容类型
     * 使用Spring框架提供的MediaType API进行更精确的判断
     * 
     * @param contentType 响应的Content-Type
     * @return true 如果是二进制类型，false 如果是文本类型
     */
    private boolean isBinaryContent(MediaType contentType) {
        if (contentType == null) {
            return false; // 无法确定时默认按文本处理
        }

        // 使用Spring提供的预定义常量进行判断，更加准确和标准

        // 1. 明确的文本类型 - 采用白名单方式更安全
        if (isTextContent(contentType)) {
            return false;
        }

        // 2. 明确的二进制类型
        if (contentType.isCompatibleWith(MediaType.APPLICATION_OCTET_STREAM) ||
                contentType.isCompatibleWith(MediaType.APPLICATION_PDF) ||
                contentType.getType().equals("image") ||
                contentType.getType().equals("audio") ||
                contentType.getType().equals("video")) {
            return true;
        }

        // 3. application 类型需要特殊处理
        if ("application".equals(contentType.getType())) {
            String subtype = contentType.getSubtype();

            // Office文档和压缩文件等二进制格式
            if (subtype.startsWith("vnd.") || // 厂商特定格式，通常是二进制
                    subtype.contains("zip") ||
                    subtype.contains("excel") ||
                    subtype.contains("word") ||
                    subtype.contains("powerpoint") ||
                    subtype.contains("msword") ||
                    subtype.contains("ms-excel") ||
                    subtype.contains("openxml")) {
                return true;
            }
        }

        // 4. 默认情况下，未知类型按文本处理（更安全的方式）
        return false;
    }

    /**
     * 判断是否为文本内容类型
     * 使用Spring MediaType API的最佳实践
     * 
     * @param contentType 内容类型
     * @return true 如果是文本类型
     */
    private boolean isTextContent(MediaType contentType) {
        // 所有 text/* 类型
        if ("text".equals(contentType.getType())) {
            return true;
        }

        // 使用Spring预定义的常量进行精确匹配
        return contentType.isCompatibleWith(MediaType.APPLICATION_JSON) ||
                contentType.isCompatibleWith(MediaType.APPLICATION_XML) ||
                contentType.isCompatibleWith(MediaType.TEXT_XML) ||
                contentType.isCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED) ||
                contentType.isCompatibleWith(MediaType.TEXT_HTML) ||
                contentType.isCompatibleWith(MediaType.TEXT_PLAIN) ||
                // 其他文本格式
                (contentType.getType().equals("application") &&
                        (contentType.getSubtype().contains("json") ||
                                contentType.getSubtype().contains("xml") ||
                                contentType.getSubtype().contains("text") ||
                                contentType.getSubtype().contains("javascript") ||
                                contentType.getSubtype().equals("x-www-form-urlencoded")));
    }

}
