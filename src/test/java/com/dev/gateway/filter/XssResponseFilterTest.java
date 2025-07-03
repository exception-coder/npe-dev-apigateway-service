package com.dev.gateway.filter;

import com.dev.gateway.config.SecurityFilterConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XSS响应体过滤器测试
 */
public class XssResponseFilterTest {

    private XssResponseFilter filter;
    private SecurityFilterConfig config;

    @BeforeEach
    void setUp() {
        config = new SecurityFilterConfig();
        config.setEnableXssCheck(true);
        config.setEnableDetailedLogging(false);
        config.setSkipPaths(new String[] { "/actuator/**", "/health" });
        config.setSkipUserAgents(new String[] { "HealthCheck" });

        filter = new XssResponseFilter(config);
    }

    @Test
    void testSanitizeJsonContent() throws Exception {
        String maliciousJson = "{\"name\":\"<script>alert('xss')</script>\",\"age\":25}";

        // 使用反射调用私有方法进行测试
        Method method = XssResponseFilter.class.getDeclaredMethod("sanitizeResponseContent", String.class,
                MediaType.class);
        method.setAccessible(true);

        String result = (String) method.invoke(filter, maliciousJson, MediaType.APPLICATION_JSON);

        assertFalse(result.contains("<script>"));
        assertTrue(result.contains("alert(&#39;xss&#39;)"));
        System.out.printf("JSON清理结果: %s%n", result);
    }

    @Test
    void testSanitizeHtmlContent() throws Exception {
        String maliciousHtml = "<p>Hello <script>alert('xss')</script> World</p>";

        Method method = XssResponseFilter.class.getDeclaredMethod("sanitizeResponseContent", String.class,
                MediaType.class);
        method.setAccessible(true);

        String result = (String) method.invoke(filter, maliciousHtml, MediaType.TEXT_HTML);

        assertFalse(result.contains("<script>"));
        assertTrue(result.contains("<p>"));
        assertTrue(result.contains("Hello"));
        System.out.printf("HTML清理结果: %s%n", result);
    }

    @Test
    void testSanitizeComplexJson() throws Exception {
        String complexJson = "{\n" +
                "  \"users\": [\n" +
                "    {\n" +
                "      \"id\": 1,\n" +
                "      \"name\": \"<script>alert('user1')</script>\",\n" +
                "      \"profile\": {\n" +
                "        \"bio\": \"<img src=x onerror=alert('bio')>\",\n" +
                "        \"website\": \"javascript:alert('site')\"\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        Method method = XssResponseFilter.class.getDeclaredMethod("sanitizeResponseContent", String.class,
                MediaType.class);
        method.setAccessible(true);

        String result = (String) method.invoke(filter, complexJson, MediaType.APPLICATION_JSON);

        assertFalse(result.contains("<script>"));
        assertFalse(result.contains("onerror"));
        assertFalse(result.contains("javascript:"));

        // 验证正常内容保留
        assertTrue(result.contains("\"id\":1"));

        System.out.printf("复杂JSON清理结果: %s%n", result);
    }

    @Test
    void testNonTextContent() throws Exception {
        String binaryContent = "binary data";

        Method method = XssResponseFilter.class.getDeclaredMethod("sanitizeResponseContent", String.class,
                MediaType.class);
        method.setAccessible(true);

        String result = (String) method.invoke(filter, binaryContent, MediaType.APPLICATION_OCTET_STREAM);

        // 非文本内容应该原样返回
        assertEquals(binaryContent, result);
    }

    @Test
    void testXmlContent() throws Exception {
        String maliciousXml = "<?xml version=\"1.0\"?><root><data><script>alert('xml')</script></data></root>";

        Method method = XssResponseFilter.class.getDeclaredMethod("sanitizeResponseContent", String.class,
                MediaType.class);
        method.setAccessible(true);

        String result = (String) method.invoke(filter, maliciousXml, MediaType.APPLICATION_XML);

        assertFalse(result.contains("<script>"));
        System.out.printf("XML清理结果: %s%n", result);
    }

    @Test
    void testEmptyContent() throws Exception {
        Method method = XssResponseFilter.class.getDeclaredMethod("sanitizeResponseContent", String.class,
                MediaType.class);
        method.setAccessible(true);

        String result1 = (String) method.invoke(filter, "", MediaType.APPLICATION_JSON);
        String result2 = (String) method.invoke(filter, null, MediaType.APPLICATION_JSON);

        assertEquals("", result1);
        assertNull(result2);
    }

    @Test
    void testPerformance() throws Exception {
        // 生成大型JSON用于性能测试
        StringBuilder jsonBuilder = new StringBuilder("{\"items\":[");
        for (int i = 0; i < 1000; i++) {
            if (i > 0)
                jsonBuilder.append(",");
            jsonBuilder.append(String.format(
                    "{\"id\":%d,\"content\":\"内容%d<script>alert('test')</script>\"}",
                    i, i));
        }
        jsonBuilder.append("]}");

        String largeJson = jsonBuilder.toString();

        Method method = XssResponseFilter.class.getDeclaredMethod("sanitizeResponseContent", String.class,
                MediaType.class);
        method.setAccessible(true);

        long startTime = System.nanoTime();
        String result = (String) method.invoke(filter, largeJson, MediaType.APPLICATION_JSON);
        long endTime = System.nanoTime();

        assertFalse(result.contains("<script>"));

        double durationMs = (endTime - startTime) / 1_000_000.0;
        System.out.printf("大型JSON处理时间: %.3f ms, 大小: %d bytes%n",
                durationMs, largeJson.length());

        // 性能断言 - 应该在合理时间内完成
        assertTrue(durationMs < 100, "处理时间应该小于100ms");
    }
}