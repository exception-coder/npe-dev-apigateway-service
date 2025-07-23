package com.dev.gateway.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.context.annotation.Bean;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

/**
 * 静态资源配置类
 * 用于配置Gateway中的静态资源访问
 */
@Configuration
public class StaticResourceConfig implements WebFluxConfigurer {

    /**
     * 配置静态资源处理器
     * 映射 /static/** 路径到 classpath:/static/ 目录
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
    }

    /**
     * 配置功能性路由处理器（备选方案）
     * 提供更灵活的静态资源访问控制
     */
    @Bean
    public RouterFunction<ServerResponse> staticResourceRouter() {
        return RouterFunctions
                .route(GET("/static/**"), request -> {
                    String path = request.path().substring("/static/".length());
                    Resource resource = new ClassPathResource("static/" + path);

                    if (resource.exists() && resource.isReadable()) {
                        MediaType mediaType = getMediaType(path);
                        return ServerResponse.ok()
                                .contentType(mediaType)
                                .bodyValue(resource);
                    } else {
                        return ServerResponse.notFound().build();
                    }
                });
    }

    /**
     * 根据文件扩展名确定媒体类型
     */
    private MediaType getMediaType(String filename) {
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        switch (extension) {
            case "html":
                return MediaType.TEXT_HTML;
            case "css":
                return MediaType.valueOf("text/css");
            case "js":
                return MediaType.valueOf("application/javascript");
            case "png":
                return MediaType.IMAGE_PNG;
            case "jpg":
            case "jpeg":
                return MediaType.IMAGE_JPEG;
            case "gif":
                return MediaType.IMAGE_GIF;
            default:
                return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}