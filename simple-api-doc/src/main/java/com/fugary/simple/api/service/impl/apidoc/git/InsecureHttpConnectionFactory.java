package com.fugary.simple.api.service.impl.apidoc.git;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.transport.http.JDKHttpConnectionFactory;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * 忽略 SSL 证书验证的 JGit HTTP 连接工厂（支持企业自建 GitLab、自签名证书与内网私有 CA）
 *
 * @author gary.fu
 */
@Slf4j
public class InsecureHttpConnectionFactory extends JDKHttpConnectionFactory {

    private static final TrustManager[] TRUST_ALL_CERTS = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }

                @Override
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    // 信任所有客户端证书
                }

                @Override
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    // 信任所有服务端自签名与私有 CA 证书
                }
            }
    };

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static final InsecureHttpConnectionFactory INSTANCE = new InsecureHttpConnectionFactory();

    @Override
    public HttpConnection create(URL url) throws IOException {
        return configure(super.create(url));
    }

    @Override
    public HttpConnection create(URL url, Proxy proxy) throws IOException {
        return configure(super.create(url, proxy));
    }

    protected HttpConnection configure(HttpConnection connection) {
        try {
            connection.configure(null, TRUST_ALL_CERTS, SECURE_RANDOM);
            connection.setHostnameVerifier((hostname, session) -> true);
        } catch (GeneralSecurityException e) {
            log.warn("JGit 配置忽略 SSL 证书异常: {}", e.getMessage());
        }
        return connection;
    }
}
