package com.dev.gateway.utils;

import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;

public class SelfSignedCertificateGenerator {

    static {
        // 注册 BouncyCastle 提供程序
        Security.addProvider(new BouncyCastleProvider());
    }

    public static KeyStore generateSelfSignedCertificate(String domain, String alias, String password) throws Exception {
        // 生成 RSA 密钥对
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        // 证书有效期
        long now = System.currentTimeMillis();
        Date startDate = new Date(now);
        Date endDate = new Date(now + 365 * 24L * 60L * 60L * 1000L); // 有效期1年

        // 设置证书的颁发者和主体信息
        X500Principal dnName = new X500Principal("CN=" + domain);

        // 使用 BouncyCastle 创建证书生成器
        BigInteger certSerialNumber = BigInteger.valueOf(now); // 证书序列号
        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                dnName, certSerialNumber, startDate, endDate, dnName, keyPair.getPublic());

        // 使用 SHA256withRSA 签名算法生成签名器
        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());

        // 生成 X509 证书
        X509Certificate certificate = new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certBuilder.build(contentSigner));

        // 创建并存储到 KeyStore 中
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);  // 初始化空的 keystore
        keyStore.setKeyEntry(alias, keyPair.getPrivate(), password.toCharArray(), new Certificate[]{certificate});

        return keyStore;
    }

    public static void main(String[] args) throws Exception {
        // 生成自签名证书并保存到文件
        String domain = "my.gateway.com";
        String alias = "mygateway";
        String password = "gatewayP@ssw0rd";
        
        KeyStore keyStore = generateSelfSignedCertificate(domain, alias, password);

        // 保存到文件
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream("mygateway.p12")) {
            keyStore.store(fos, password.toCharArray());
        }

        System.out.println("自签名证书已生成并保存在 mygateway.p12 文件中");
    }
}