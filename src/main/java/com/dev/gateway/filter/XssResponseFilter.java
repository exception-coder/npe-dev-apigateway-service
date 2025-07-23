package com.dev.gateway.filter;

import com.dev.gateway.config.SecurityFilterConfig;
import com.dev.gateway.service.IpResolverService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.extern.slf4j.Slf4j;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.ObjectUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;

/**
 * XSS响应体清理过滤器
 * 使用OWASP HTML Sanitizer对响应体内容进行XSS清理
 */
@Slf4j
@Component
public class XssResponseFilter implements GatewayFilter {

    // OWASP HTML Sanitizer策略配置
    private static final PolicyFactory XSS_POLICY = Sanitizers.FORMATTING
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.TABLES)
            .and(Sanitizers.LINKS)
            .and(Sanitizers.STYLES)
            .and(Sanitizers.IMAGES);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final SecurityFilterConfig config;

    private final IpResolverService ipResolverService;

    public XssResponseFilter(SecurityFilterConfig config, IpResolverService ipResolverService) {
        this.config = config;
        this.ipResolverService = ipResolverService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 检查是否跳过此请求
        if (shouldSkipRequest(exchange)) {
            return chain.filter(exchange);
        }

        // 装饰响应体以进行XSS清理
        ServerHttpResponse originalResponse = exchange.getResponse();
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (body instanceof Flux) {
                    Flux<? extends DataBuffer> fluxBody = (Flux<? extends DataBuffer>) body;
                    return super.writeWith(fluxBody.buffer().map(dataBuffers -> {
                        // 合并所有DataBuffer
                        DataBufferFactory bufferFactory = originalResponse.bufferFactory();
                        DataBuffer joinedBuffer = bufferFactory.join(dataBuffers);

                        try {
                            // 获取响应内容
                            String content = joinedBuffer.toString(StandardCharsets.UTF_8);

                            // 清理响应体内容
                            String sanitizedContent = sanitizeResponseContent(content,
                                    originalResponse.getHeaders().getContentType());

                            // 返回清理后的内容
                            return bufferFactory.wrap(sanitizedContent.getBytes(StandardCharsets.UTF_8));
                        } finally {
                            // 释放原buffer
                            DataBufferUtils.release(joinedBuffer);
                        }
                    }));
                }
                return super.writeWith(body);
            }
        };

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    /**
     * 判断是否应该跳过此请求的XSS清理
     */
    private boolean shouldSkipRequest(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().toString();
        String userAgent = exchange.getRequest().getHeaders().getFirst("User-Agent");

        // 检查路径白名单
        for (String skipPath : config.getSkipPaths()) {
            if (PATH_MATCHER.match(skipPath, path)) {
                log.debug("跳过路径XSS清理: {}", path);
                return true;
            }
        }

        // 检查User-Agent白名单
        if (userAgent != null) {
            for (String skipUserAgent : config.getSkipUserAgents()) {
                if (userAgent.contains(skipUserAgent)) {
                    log.debug("跳过User-Agent XSS清理: {}", userAgent);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 清理响应体内容
     */
    private String sanitizeResponseContent(String content, MediaType contentType) {
        if (ObjectUtils.isEmpty(content)) {
            return content;
        }

        try {
            if (MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
                return sanitizeJsonContent(content);
            } else if (MediaType.TEXT_HTML.isCompatibleWith(contentType)) {
                return sanitizeHtmlContent(content);
            } else if (MediaType.APPLICATION_XML.isCompatibleWith(contentType) ||
                    MediaType.TEXT_XML.isCompatibleWith(contentType)) {
                return sanitizeXmlContent(content);
            } else if (contentType != null && contentType.getType().equals("text")) {
                return sanitizeTextContent(content);
            } else {
                // 对于其他类型，不进行处理
                log.debug("跳过非文本类型内容的XSS清理: {}", contentType);
                return content;
            }
        } catch (Exception e) {
            log.warn("XSS清理失败，返回原始内容: {}", e.getMessage());
            return content;
        }
    }

    /**
     * 清理JSON内容
     */
    private String sanitizeJsonContent(String jsonContent) throws Exception {
        JsonNode rootNode = OBJECT_MAPPER.readTree(jsonContent);
        JsonNode sanitizedNode = sanitizeJsonNode(rootNode);
        return OBJECT_MAPPER.writeValueAsString(sanitizedNode);
    }

    /**
     * 递归清理JSON节点
     */
    private JsonNode sanitizeJsonNode(JsonNode node) {
        if (node.isTextual()) {
            // 对文本节点进行XSS清理
            String originalText = node.asText();
            String sanitizedText = XSS_POLICY.sanitize(originalText);
            return new TextNode(sanitizedText);
        } else if (node.isObject()) {
            // 处理对象节点
            ObjectNode objectNode = OBJECT_MAPPER.createObjectNode();
            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                JsonNode fieldValue = node.get(fieldName);
                objectNode.set(fieldName, sanitizeJsonNode(fieldValue));
            }
            return objectNode;
        } else if (node.isArray()) {
            // 处理数组节点
            ArrayNode arrayNode = OBJECT_MAPPER.createArrayNode();
            for (JsonNode arrayElement : node) {
                arrayNode.add(sanitizeJsonNode(arrayElement));
            }
            return arrayNode;
        } else {
            // 其他类型节点（数字、布尔值、null等）直接返回
            return node;
        }
    }

    /**
     * 清理HTML内容
     */
    private String sanitizeHtmlContent(String htmlContent) {
        return XSS_POLICY.sanitize(htmlContent);
    }

    /**
     * 清理XML内容
     */
    private String sanitizeXmlContent(String xmlContent) {
        // 对XML内容进行基本的XSS清理
        return XSS_POLICY.sanitize(xmlContent);
    }

    /**
     * 清理文本内容
     */
    private String sanitizeTextContent(String textContent) {
        return XSS_POLICY.sanitize(textContent);
    }

    /**
     * 用于modifyResponseBody的响应体清理方法
     * 
     * @param exchange ServerWebExchange对象
     * @param body     原始响应体字节数组
     * @return 清理后的响应体
     */
    public Mono<byte[]> sanitizeResponseBody(ServerWebExchange exchange, byte[] body) {
        return Mono.fromCallable(() -> {
            // 检查是否跳过此请求
            if (shouldSkipRequest(exchange)) {
                return body;
            }

            // 获取响应内容类型
            MediaType contentType = exchange.getResponse().getHeaders().getContentType();

            // 转换为字符串进行处理
            String content = new String(body, StandardCharsets.UTF_8);

            // 清理响应体内容
            String sanitizedContent = sanitizeResponseContent(content, contentType);

            // 返回清理后的字节数组
            return sanitizedContent.getBytes(StandardCharsets.UTF_8);
        });
    }
}