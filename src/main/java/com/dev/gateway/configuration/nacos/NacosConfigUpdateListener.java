package com.dev.gateway.configuration.nacos;

import com.alibaba.nacos.api.config.annotation.NacosConfigListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NacosConfigUpdateListener {

    @NacosConfigListener(dataId = "microservice-apigateway-service-dev.yml")
    public void onConfigChange(String updatedConfig) {
        // 打印新配置，确认是否收到了最新的配置信息
        System.out.println("Updated Nacos Config: " + updatedConfig);
        log.info("Updated Nacos Config: " + updatedConfig);
    }
}