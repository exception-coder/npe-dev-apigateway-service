package com.dev.gateway.utils;

import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.springframework.stereotype.Component;

/**
 * Jasypt 配置加密工具类
 * 用于对配置文件中的敏感信息进行加密和解密
 * 
 * @author gateway-service
 */
@Component
public class JasyptUtil {

    /**
     * 默认加密密钥（生产环境应通过环境变量或启动参数传入）
     */
    private static final String DEFAULT_PASSWORD = "GatewayConfigEncryptKey2024!";

    /**
     * 获取字符串加密器
     * 
     * @param password 加密密钥
     * @return StringEncryptor
     */
    public static StringEncryptor getStringEncryptor(String password) {
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();

        // 设置加密密钥
        config.setPassword(password);
        // 设置加密算法
        config.setAlgorithm("PBEWITHHMACSHA512ANDAES_256");
        // 设置key获取迭代次数
        config.setKeyObtentionIterations("1000");
        // 设置池大小
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

    /**
     * 使用默认密钥获取字符串加密器
     * 
     * @return StringEncryptor
     */
    public static StringEncryptor getStringEncryptor() {
        return getStringEncryptor(DEFAULT_PASSWORD);
    }

    /**
     * 加密字符串
     * 
     * @param plaintext 明文
     * @return 加密后的字符串
     */
    public static String encrypt(String plaintext) {
        return getStringEncryptor().encrypt(plaintext);
    }

    /**
     * 加密字符串（指定密钥）
     * 
     * @param plaintext 明文
     * @param password  加密密钥
     * @return 加密后的字符串
     */
    public static String encrypt(String plaintext, String password) {
        return getStringEncryptor(password).encrypt(plaintext);
    }

    /**
     * 解密字符串
     * 
     * @param ciphertext 密文
     * @return 解密后的字符串
     */
    public static String decrypt(String ciphertext) {
        return getStringEncryptor().decrypt(ciphertext);
    }

    /**
     * 解密字符串（指定密钥）
     * 
     * @param ciphertext 密文
     * @param password   加密密钥
     * @return 解密后的字符串
     */
    public static String decrypt(String ciphertext, String password) {
        return getStringEncryptor(password).decrypt(ciphertext);
    }

    /**
     * 测试方法 - 用于生成加密后的配置值
     */
    public static void main(String[] args) {
        String password = DEFAULT_PASSWORD;

        // 需要加密的敏感信息
        String[] secrets = {
                "mongo", // MongoDB 密码
                "redis", // Redis 密码
                "gateway", // SSL keystore 密码
                "nacos", // Nacos 密码
        };

        System.out.println("=== 配置加密结果 ===");
        System.out.println("加密密钥: " + password);
        System.out.println();

        for (String secret : secrets) {
            String encrypted = encrypt(secret, password);
            System.out.println("原文: " + secret);
            System.out.println("密文: ENC(" + encrypted + ")");
            System.out.println("验证解密: " + decrypt(encrypted, password));
            System.out.println("---");
        }
    }
}