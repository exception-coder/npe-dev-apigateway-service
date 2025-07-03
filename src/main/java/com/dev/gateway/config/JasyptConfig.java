package com.dev.gateway.config;

import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jasypt 配置类
 * 用于Spring Boot集成配置加密功能
 * 
 * @author gateway-service
 */
@Configuration
public class JasyptConfig {

    /**
     * 从环境变量或配置文件中获取加密密钥
     * 默认值为开发环境密钥，生产环境应通过环境变量覆盖
     */
    @Value("${jasypt.encryptor.password:GatewayConfigEncryptKey2024!}")
    private String encryptorPassword;

    /**
     * 配置字符串加密器Bean
     * 
     * @return StringEncryptor
     */
    @Bean("jasyptStringEncryptor")
    public StringEncryptor stringEncryptor() {
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();
        // 设置加密密钥
        config.setPassword(encryptorPassword);
        // 设置加密算法（AES 256位加密）
        config.setAlgorithm("PBEWITHHMACSHA512ANDAES_256");
        // 设置key获取迭代次数
        config.setKeyObtentionIterations("1000");
        // 设置池大小（支持并发）
        config.setPoolSize("1");
        // 设置盐值生成器
        config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
        // 设置IV生成器
        config.setIvGeneratorClassName("org.jasypt.iv.RandomIvGenerator");
        // 设置字符串输出类型
        config.setStringOutputType("base64");

        encryptor.setConfig(config);
        return encryptor;
    }
}