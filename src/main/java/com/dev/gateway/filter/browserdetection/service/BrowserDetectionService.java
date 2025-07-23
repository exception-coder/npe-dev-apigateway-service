package com.dev.gateway.filter.browserdetection.service;

import com.dev.gateway.filter.browserdetection.properties.BrowserDetectionProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * 浏览器检测服务类
 * 负责识别请求是否来自真实浏览器
 */
@Service
@Slf4j
public class BrowserDetectionService {

    @Autowired
    private BrowserDetectionProperties properties;

    // 常见移动设备User-Agent模式
    private static final Pattern MOBILE_PATTERN = Pattern.compile(
            "(?i).*(Android|iPhone|iPad|iPod|BlackBerry|Windows Phone|Mobile).*");

    // JavaScript支持标识
    private static final String JS_ENABLED_HEADER = "X-Requested-With";
    private static final String JS_ENABLED_VALUE = "XMLHttpRequest";

    /**
     * 判断请求是否应该跳过检测
     */
    public boolean shouldSkipDetection(String requestUri) {
        return Arrays.stream(properties.getSkipPaths())
                .anyMatch(pattern -> requestUri.matches(pattern.replace("**", ".*")));
    }

    /**
     * 检测请求是否来自真实浏览器
     */
    public BrowserDetectionResult detectBrowser(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        BrowserDetectionResult result = new BrowserDetectionResult();

        if (properties.isVerboseLogging()) {
            log.debug("开始浏览器检测 - URI: {}", request.getURI().getPath());
        }

        // 1. 检查User-Agent
        DetectionScore userAgentScore = checkUserAgent(request);
        result.setUserAgentScore(userAgentScore);

        // 2. 检查请求头
        DetectionScore headerScore = checkHeaders(request);
        result.setHeaderScore(headerScore);

        // 3. 检查JavaScript支持（如果启用）
        DetectionScore jsScore = checkJavaScriptSupport(request);
        result.setJavaScriptScore(jsScore);

        // 4. 综合评分
        result.calculateFinalScore(properties.getStrictness());

        if (properties.isVerboseLogging()) {
            log.debug("浏览器检测结果 - 最终得分: {}, 是否为浏览器: {}", 
                    result.getFinalScore(), result.isBrowser());
        }

        return result;
    }

    /**
     * 检查User-Agent
     */
    private DetectionScore checkUserAgent(ServerHttpRequest request) {
        String userAgent = request.getHeaders().getFirst(HttpHeaders.USER_AGENT);
        DetectionScore score = new DetectionScore();

        if (!StringUtils.hasText(userAgent)) {
            score.addPenalty(50, "缺少User-Agent");
            return score;
        }

        // 检查长度
        if (userAgent.length() < properties.getMinUserAgentLength()) {
            score.addPenalty(30, "User-Agent过短");
        }
        if (userAgent.length() > properties.getMaxUserAgentLength()) {
            score.addPenalty(20, "User-Agent过长");
        }

        // 检查是否包含已知爬虫标识
        String lowerUserAgent = userAgent.toLowerCase();
        for (String botKeyword : properties.getBotUserAgentsList()) {
            if (lowerUserAgent.contains(botKeyword.toLowerCase())) {
                score.addPenalty(80, "包含爬虫标识: " + botKeyword);
                break; // 找到一个就足够了
            }
        }

        // 检查是否包含真实浏览器标识
        boolean hasBrowserSignature = false;
        for (String browserKeyword : properties.getRealBrowserUserAgentsList()) {
            if (userAgent.contains(browserKeyword)) {
                score.addBonus(20, "包含浏览器标识: " + browserKeyword);
                hasBrowserSignature = true;
                break;
            }
        }

        if (!hasBrowserSignature) {
            score.addPenalty(40, "缺少浏览器标识");
        }

        // 检查是否为移动设备
        if (MOBILE_PATTERN.matcher(userAgent).matches()) {
            score.addBonus(10, "移动设备浏览器");
        }

        // 检查User-Agent的复杂性（真实浏览器通常有复杂的User-Agent）
        if (userAgent.contains("(") && userAgent.contains(")") && userAgent.contains(";")) {
            score.addBonus(15, "User-Agent结构复杂");
        } else {
            score.addPenalty(25, "User-Agent结构简单");
        }

        return score;
    }

    /**
     * 检查请求头
     */
    private DetectionScore checkHeaders(ServerHttpRequest request) {
        DetectionScore score = new DetectionScore();
        HttpHeaders headers = request.getHeaders();

        // 检查必需的浏览器请求头
        int missingHeaders = 0;
        for (String requiredHeader : properties.getRequiredBrowserHeadersList()) {
            if (!headers.containsKey(requiredHeader)) {
                missingHeaders++;
                score.addPenalty(15, "缺少必需请求头: " + requiredHeader);
            } else {
                score.addBonus(5, "包含必需请求头: " + requiredHeader);
            }
        }

        // 如果缺少太多必需请求头，给予重罚
        if (missingHeaders > 2) {
            score.addPenalty(30, "缺少过多必需请求头");
        }

        // 检查Accept头的复杂性
        String accept = headers.getFirst(HttpHeaders.ACCEPT);
        if (StringUtils.hasText(accept)) {
            if (accept.contains("text/html") && accept.contains("*/*")) {
                score.addBonus(15, "Accept头符合浏览器特征");
            } else if (accept.equals("*/*")) {
                score.addPenalty(20, "Accept头过于简单");
            }
        }

        // 检查Accept-Language
        String acceptLanguage = headers.getFirst(HttpHeaders.ACCEPT_LANGUAGE);
        if (StringUtils.hasText(acceptLanguage)) {
            if (acceptLanguage.contains(",") && acceptLanguage.contains("q=")) {
                score.addBonus(10, "Accept-Language包含质量值");
            }
        }

        // 检查Accept-Encoding
        String acceptEncoding = headers.getFirst(HttpHeaders.ACCEPT_ENCODING);
        if (StringUtils.hasText(acceptEncoding)) {
            if (acceptEncoding.contains("gzip") || acceptEncoding.contains("deflate")) {
                score.addBonus(10, "支持压缩编码");
            }
        }

        // 检查可疑请求头
        for (String suspiciousHeader : properties.getSuspiciousHeadersList()) {
            if (headers.containsKey(suspiciousHeader)) {
                score.addPenalty(10, "包含可疑请求头: " + suspiciousHeader);
            }
        }

        // 检查Connection头
        String connection = headers.getFirst(HttpHeaders.CONNECTION);
        if ("keep-alive".equalsIgnoreCase(connection)) {
            score.addBonus(5, "使用keep-alive连接");
        }

        return score;
    }

    /**
     * 检查JavaScript支持
     */
    private DetectionScore checkJavaScriptSupport(ServerHttpRequest request) {
        DetectionScore score = new DetectionScore();

        if (!properties.isCheckJavaScriptSupport()) {
            return score; // 如果不检查JS支持，返回中性得分
        }

        // 检查是否有JavaScript相关的请求头
        String requestedWith = request.getHeaders().getFirst(JS_ENABLED_HEADER);
        if (JS_ENABLED_VALUE.equals(requestedWith)) {
            score.addBonus(20, "AJAX请求，支持JavaScript");
        }

        // 检查Referer头（通常由JavaScript设置）
        String referer = request.getHeaders().getFirst(HttpHeaders.REFERER);
        if (StringUtils.hasText(referer)) {
            score.addBonus(10, "包含Referer头");
        }

        return score;
    }

    /**
     * 获取检测统计信息
     */
    public BrowserDetectionStats getStats() {
        BrowserDetectionStats stats = new BrowserDetectionStats();
        stats.setEnabled(properties.isEnabled());
        stats.setStrictness(properties.getStrictness().toString());
        stats.setSkipPathsCount(properties.getSkipPaths().length);
        stats.setBotUserAgentsCount(properties.getBotUserAgents().length);
        return stats;
    }

    /**
     * 检测得分类
     */
    @Data
    public static class DetectionScore {
        private int score = 0;
        private StringBuilder reasons = new StringBuilder();

        public void addPenalty(int penalty, String reason) {
            score -= penalty;
            if (reasons.length() > 0) reasons.append("; ");
            reasons.append("-").append(penalty).append(": ").append(reason);
        }

        public void addBonus(int bonus, String reason) {
            score += bonus;
            if (reasons.length() > 0) reasons.append("; ");
            reasons.append("+").append(bonus).append(": ").append(reason);
        }

        public String getReasons() {
            return reasons.toString();
        }
    }

    /**
     * 浏览器检测结果类
     */
    @Data
    public static class BrowserDetectionResult {
        private DetectionScore userAgentScore;
        private DetectionScore headerScore;
        private DetectionScore javaScriptScore;
        private int finalScore;
        private boolean isBrowser;
        private String rejectionReason;

        public void calculateFinalScore(BrowserDetectionProperties.StrictnessLevel strictness) {
            finalScore = userAgentScore.getScore() + headerScore.getScore() + javaScriptScore.getScore();

            // 根据严格程度设定阈值
            int threshold;
            switch (strictness) {
                case STRICT:
                    threshold = 50;   // 严格模式需要更高分数
                    break;
                case MODERATE:
                    threshold = 20;   // 中等模式
                    break;
                case LOOSE:
                    threshold = -20;  // 宽松模式允许负分
                    break;
                default:
                    threshold = 20;   // 默认中等模式
                    break;
            }

            isBrowser = finalScore >= threshold;

            if (!isBrowser) {
                rejectionReason = String.format("浏览器检测失败 - 得分: %d (阈值: %d). 详情: UA(%s), Headers(%s), JS(%s)",
                        finalScore, threshold,
                        userAgentScore.getReasons(),
                        headerScore.getReasons(),
                        javaScriptScore.getReasons());
            }
        }
    }

    /**
     * 浏览器检测统计信息类
     */
    @Data
    public static class BrowserDetectionStats {
        private boolean enabled;
        private String strictness;
        private int skipPathsCount;
        private int botUserAgentsCount;
    }
} 