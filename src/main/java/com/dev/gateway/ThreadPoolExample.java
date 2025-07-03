package com.dev.gateway;

import cn.hutool.core.date.DateUtil;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.net.ssl.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadPoolExample {

    static final SSLContext sslContext;
    static final TrustManager[] trustAllCerts;

    static {
        try {
            trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };
            sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }

    static OkHttpClient client = new OkHttpClient().newBuilder()
            .followRedirects(false)
            .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0])
            .hostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String s, SSLSession sslSession) {
                    // 忽略 hostname 验证
                    return true;
                }
            })
            .build();
    public static void main(String[] args) throws Exception {
        // 创建一个固定大小的线程池
        ExecutorService executorService = Executors.newFixedThreadPool(50);
        // 提交 50 个任务
        for (int i = 0; i < 100000; i++) {
            final int taskId = i;
            executorService.submit(() -> {
                Request request = new Request.Builder()
//                        .url("http://127.0.0.1:9090/GrandbuySystem/loginController.do?login#")
//                        .url("https://127.0.0.1/dev/static/captcha.html")
//                        .url("https://localhost")
                        .url("https://www.1lzs.com/")
                        .method("GET", null)
                        .addHeader("Mock-IP", "127.0.0." + taskId)
//                            .addHeader("Mock-IP", "127.0.0.1")
                        .build();
                try(Response response = client.newCall(request).execute()) {
                    System.err.println(response.code()+" "+ DateUtil.formatDateTime(DateUtil.date()));
//                    System.out.println(response.body().string());
                } catch (Exception e) {
                    e.printStackTrace();
                    Thread.currentThread().interrupt();
                }
            });
        }

        // 关闭线程池，不再接受新任务
        executorService.shutdown();

        // 等待所有任务完成
        try {
            if (!executorService.awaitTermination(300, java.util.concurrent.TimeUnit.SECONDS)) {
                executorService.shutdownNow(); // 超时则强制关闭
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }

        System.out.println("所有任务完成，主线程退出.");
    }
}