package com.dev.gateway.controller;

import com.alibaba.fastjson.JSONObject;
import com.dev.gateway.filter.ratelimit.service.RateLimitService;
import com.dev.gateway.service.IpResolverService;
import com.dev.gateway.utils.skywalking.LogContextUtil;
import com.google.code.kaptcha.Producer;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Objects;

@Slf4j
@RestController
public class CaptchaController {

    private final Producer captchaProducer;

    private final RateLimitService rateLimitService;

    private final IpResolverService ipResolverService;

    public CaptchaController(Producer captchaProducer,
            RateLimitService rateLimitService,
            IpResolverService ipResolverService) {
        this.captchaProducer = captchaProducer;
        this.rateLimitService = rateLimitService;
        this.ipResolverService = ipResolverService;
    }

    @GetMapping("/captcha-info")
    public Mono<Object> getCaptchaInfo(ServerWebExchange exchange) throws IOException {
        // 由于Redis操作是异步的，我们需要组合多个Mono来获取所有信息
        return Mono.zip(
                rateLimitService.getLast10SecondsReqIpCount(),
                rateLimitService.getCaptchaRequired()).map(tuple -> {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("last10SecondsReqIpsCount", tuple.getT1());
                    jsonObject.put("captchaRequired", tuple.getT2());
                    return jsonObject;
                });
    }

    @Deprecated
    @GetMapping(value = "/static/captcha", produces = MediaType.TEXT_HTML_VALUE)
    public Mono<String> captcha() {
        String html = "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <title>Captcha</title>\n" +
                "</head>\n" +
                "<body>\n" +
                "\n" +
                "<form action=\"/validate-captcha\" method=\"post\">\n" +
                "    <label for=\"captcha\">Enter Captcha:</label>\n" +
                "    <input type=\"text\" name=\"captcha\" id=\"captcha\" required>\n" +
                "    <br>\n" +
                "    <img src=\"/captcha\" alt=\"Captcha Image\" onclick=\"this.src='/captcha?' + Math.random();\" style=\"cursor:pointer;\">\n"
                +
                "    <br>\n" +
                "    <input type=\"submit\" value=\"Submit\">\n" +
                "</form>\n" +
                "\n" +
                "\n" +
                "</body>\n" +
                "</html>";
        return Mono.just(html);
    }

    /**
     *
     * @param exchange
     * @return
     */
    @PostMapping("/validate-captcha")
    public Mono<Void> validateCaptcha(ServerWebExchange exchange) {
        exchange.getAttributes().put("flatMap", false);
        return exchange.getFormData().flatMap(formData -> {
            String clientIp = ipResolverService.getClientIp(exchange);
            String captcha = formData.getFirst("captcha");
            log.info("ip:{},验证码:{}", clientIp, captcha);

            return rateLimitService.getCaptcha1MinutesWithIp(clientIp)
                    .flatMap(storedCaptcha -> validateCaptcha(exchange, storedCaptcha, captcha, clientIp));
        });
    }

    private @NotNull Mono<Void> validateCaptcha(ServerWebExchange exchange, String storedCaptcha, String captcha,
            String clientIp) {
        if (captcha.equals(storedCaptcha)) {
            log.info("验证码验证成功");
            exchange.getResponse().setStatusCode(HttpStatus.FOUND);

            // 验证成功：先从黑名单移除，再添加到白名单
            return rateLimitService.removeFromBlackList(clientIp)
                    .doOnNext(blacklistRemoved -> {
                        if (blacklistRemoved) {
                            log.info("验证码验证成功，IP已从黑名单移除 - IP: {}", clientIp);
                        } else {
                            log.debug("IP不在黑名单中或移除失败 - IP: {}", clientIp);
                        }
                    })
                    .then(rateLimitService.setWhiteList5minutes(clientIp))
                    .flatMap(success -> {
                        log.info("验证码验证成功，IP已添加到白名单 - IP: {}", clientIp);
                        exchange.getResponse().getHeaders().setLocation(URI.create("/"));
                        return exchange.getResponse().setComplete().doFinally(Objects.requireNonNull(signalType -> {
                            MDC.remove("clientIp");
                            MDC.remove("uriPath");
                        }));
                    });
        } else {
            log.info("验证码验证失败");
            exchange.getResponse().setStatusCode(HttpStatus.FOUND);
            exchange.getResponse().getHeaders().setLocation(URI.create("/static/captcha"));
            return exchange.getResponse().setComplete().doFinally(Objects.requireNonNull(signalType -> {
                MDC.remove("clientIp");
                MDC.remove("uriPath");
            }));
        }
    }

    @GetMapping("/captcha")
    public Mono<Void> getCaptcha(ServerWebExchange exchange) throws IOException {
        ServerHttpResponse response = exchange.getResponse();
        ServerHttpRequest request = exchange.getRequest();
        response.getHeaders().setContentType(MediaType.IMAGE_JPEG);
        // 生成验证码文本
        String captchaText = captchaProducer.createText();
        String clientIp = ipResolverService.getClientIp(exchange);
        LogContextUtil.initSkywalkingTraceContext();
        return rateLimitService.setIpWithCaptcha1Minutes(clientIp, captchaText)
                .flatMap(success -> writeCaptchaImage(captchaText, response))
                .doOnSuccess(result -> log.info("设置验证码完成,ip:{},验证码:{}", clientIp, captchaText))
                .doOnError(error -> log.error("设置验证码失败，ip:{}, 错误: {}", clientIp, error.getMessage()));
    }

    private @NotNull Mono<Void> writeCaptchaImage(String captchaText, ServerHttpResponse response) {
        try {
            // 生成验证码图像（假设你有生成图像的逻辑）
            BufferedImage captchaImage = captchaProducer.createImage(captchaText);

            // 将图像写入 ByteArrayOutputStream
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ImageIO.write(captchaImage, "jpeg", byteArrayOutputStream);

            // 将图像字节转换为 byte[]
            byte[] imageBytes = byteArrayOutputStream.toByteArray();

            // 设置响应头
            response.getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_JPEG_VALUE);

            // 将字节数据写入到响应中
            DataBuffer buffer = response.bufferFactory().wrap(imageBytes);

            return response.writeWith(Mono.just(buffer)).doOnError(error -> {
                // 如果有异常，释放 DataBuffer
                DataBufferUtils.release(buffer);
            });

        } catch (IOException e) {
            log.error("返回验证码图片异常,异常信息:{}", e.getMessage());
            return Mono.empty();
        }
    }
}