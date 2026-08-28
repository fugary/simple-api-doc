package com.fugary.simple.api.utils.http;

import com.fugary.simple.api.exception.SimpleRuntimeException;
import com.fugary.simple.api.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.protocol.HttpClientContext;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import com.fugary.simple.api.web.vo.SimpleResult;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * HttpClient工具 <br>
 * Created on 2020/5/1 14:56 .<br>
 *
 * @author gary.fu
 */
@Slf4j
public class SimpleHttpClientUtils {

    private static final Logger logger = LoggerFactory.getLogger(SimpleHttpClientUtils.class);

    private SimpleHttpClientUtils() {
    }

    public static final PoolingHttpClientConnectionManager CLIENT_CONNECTION_MANAGER = new PoolingHttpClientConnectionManager();

    public static final RequestConfig REQUEST_CONFIG;

    public static final int POOL_SIZE = 200; // HttpClient连接池大小

    public static final int MAX_PER_ROUTE = 100;

    public static final int CONNECT_TIMEOUT = 15000; // 连接超时

    public static final int SOCKET_TIMEOUT = 60000; // 数据获取超时

    private static TrustManager[] trustManagers;

    static {
        CLIENT_CONNECTION_MANAGER.setMaxTotal(POOL_SIZE);
        CLIENT_CONNECTION_MANAGER.setDefaultMaxPerRoute(MAX_PER_ROUTE);
        REQUEST_CONFIG = RequestConfig.custom().setConnectTimeout(CONNECT_TIMEOUT).setConnectionRequestTimeout(CONNECT_TIMEOUT).setSocketTimeout(SOCKET_TIMEOUT).build();
    }

    public static final int DEFAULT_RETRY_COUNT = 3;

    public static final long DEFAULT_RETRY_INTERVAL_MS = 500L;

    public static final HttpRequestRetryHandler DEFAULT_RETRY_HANDLER = (exception, executionCount, context) -> {
        if (executionCount > DEFAULT_RETRY_COUNT) {
            return false;
        }
        if (exception instanceof UnknownHostException) {
            // DNS 无法解析
            return false;
        }
        if (exception instanceof InterruptedIOException && !(exception instanceof SocketTimeoutException || exception instanceof org.apache.http.conn.ConnectTimeoutException)) {
            // 线程中断（非超时）
            return false;
        }
        if (exception instanceof SSLException) {
            // SSL 异常：如果是网络连接重置或关闭导致的 SSLException 则进行重试
            String msg = exception.getMessage() != null ? exception.getMessage().toLowerCase() : "";
            return msg.contains("reset") || msg.contains("broken pipe") || msg.contains("closed") || msg.contains("peer");
        }
        if (exception instanceof NoHttpResponseException || exception instanceof SocketException) {
            // 连接重置（Connection reset）、Broken pipe、超时或服务器未响应
            return true;
        }
        HttpClientContext clientContext = HttpClientContext.adapt(context);
        HttpRequest request = clientContext.getRequest();
        return !(request instanceof HttpEntityEnclosingRequest);
    };

    /**
     * 判断是否为不可恢复的业务错误（如 401 未授权、403 禁止访问、404 不存在），跳过重试
     *
     * @param result 请求结果
     * @return 是否不可重试
     */
    public static boolean isNonRetryableError(SimpleResult<?> result) {
        if (result == null) {
            return false;
        }
        String msg = StringUtils.defaultString(result.getMessage());
        return msg.contains("401") || msg.contains("403") || msg.contains("404") || msg.contains("未找到") || msg.contains("Token");
    }

    /**
     * 通用 HTTP 请求重试调度器（支持阶梯延迟退避）
     *
     * @param requestAction 请求执行动作
     * @param maxRetries    最大重试次数
     * @param intervalMs    基础重试间隔时间（毫秒）
     * @param actionDesc    动作描述（用于日志）
     * @param <T>           返回数据类型
     * @return 请求结果
     */
    public static <T> SimpleResult<T> executeWithRetry(Supplier<SimpleResult<T>> requestAction, int maxRetries, long intervalMs, String actionDesc) {
        SimpleResult<T> lastResult = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            lastResult = requestAction.get();
            if (lastResult != null && lastResult.isSuccess()) {
                return lastResult;
            }
            if (isNonRetryableError(lastResult)) {
                return lastResult;
            }
            if (attempt < maxRetries) {
                long waitTime = intervalMs * attempt;
                String errorMsg = lastResult != null ? lastResult.getMessage() : "null";
                log.warn("HTTP {}失败，正在进行第 {}/{} 次重试（等待 {}ms）: error={}", actionDesc, attempt, maxRetries, waitTime, errorMsg);
                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return lastResult;
    }

    /**
     * 通用 HTTP 请求重试调度器（使用默认重试次数与退避间隔）
     *
     * @param requestAction 请求执行动作
     * @param actionDesc    动作描述（用于日志）
     * @param <T>           返回数据类型
     * @return 请求结果
     */
    public static <T> SimpleResult<T> executeWithRetry(Supplier<SimpleResult<T>> requestAction, String actionDesc) {
        return executeWithRetry(requestAction, DEFAULT_RETRY_COUNT, DEFAULT_RETRY_INTERVAL_MS, actionDesc);
    }

    /**
     * 获取httpclient
     *
     * @return
     */
    public static CloseableHttpClient getHttpClient() {
        return HttpClientBuilder.create()
                .setDefaultRequestConfig(REQUEST_CONFIG)
                .setConnectionManager(CLIENT_CONNECTION_MANAGER)
                .setRetryHandler(DEFAULT_RETRY_HANDLER)
                .build();
    }

    public static TrustManager[] getTrustManagers() {
        if (trustManagers == null) {
            setTrustManagers(new TrustManager[0]);
            try {
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init((KeyStore) null);
                setTrustManagers(tmf.getTrustManagers());
            } catch (NoSuchAlgorithmException | KeyStoreException e) {
                log.error("获取TrustManager失败", e);
            }
        }
        return trustManagers;
    }

    public static void setTrustManagers(TrustManager[] trustManagers) {
        SimpleHttpClientUtils.trustManagers = trustManagers;
    }

    public static CloseableHttpClient getHttpsClient() {
        return getHttpsClient(true);
    }

    public static CloseableHttpClient getHttpsClient(boolean cookie) {
        try {
            SSLContext ctx = SSLContext.getInstance("TLSv1.2");
            TrustManager[] trustManagers = getTrustManagers();
            ctx.init(null, trustManagers, null);
            SSLConnectionSocketFactory ssf = new SSLConnectionSocketFactory(ctx, new NoopHostnameVerifier());
            HttpClientBuilder clientBuilder = HttpClientBuilder.create()
                    .setDefaultRequestConfig(REQUEST_CONFIG)
                    .setSSLSocketFactory(ssf)
                    .setRetryHandler(DEFAULT_RETRY_HANDLER);
            if (!cookie) {
                clientBuilder.disableCookieManagement();
            }
            return clientBuilder.build();
        } catch (Exception e) {
            return getHttpClient();
        }
    }

    public static RequestConfig getRequestConfig(int connectTimeout, int soTimeout) {
        return RequestConfig.custom().setConnectTimeout(connectTimeout).setConnectionRequestTimeout(connectTimeout).setSocketTimeout(soTimeout).build();
    }

    /**
     * 发送简单get请求
     *
     * @param getUrl
     * @param clazz
     * @param paramMap
     * @param <T>
     * @return
     */
    public static <T> T sendHttpGet(String getUrl, Class<T> clazz, Map<String, String> paramMap){
        List<NameValuePair> params = paramMap.entrySet().stream()
                .map(entry -> new BasicNameValuePair(entry.getKey(), entry.getValue())).collect(Collectors.toList());
        return sendHttpGet(getUrl, clazz, (httpClient, httpGet) -> {
            try {
                URI url = new URIBuilder(getUrl).addParameters(params).build();
                ((HttpGet) httpGet).setURI(url);
            } catch (URISyntaxException e) {
                logger.error("处理URL错误", e);
            }
        }, null);
    }

    /**
     * 发送GET请求
     *
     * @param url      请求地址
     * @param clazz    返回类型
     * @param callback 回调，构建请求
     * @param <T>
     * @return
     */
    public static <T> T sendHttpGet(String url, Class<T> clazz, HttpClientRequestCallback callback) {
        return sendHttpGet(url, clazz, callback, null);
    }

    /**
     * 发送GET请求
     *
     * @param url      请求地址
     * @param clazz    返回类型
     * @param callback 回调，构建请求
     * @param responseCallback 响应处理
     * @param <T>
     * @return
     */
    public static <T> T sendHttpGet(String url, Class<T> clazz, HttpClientRequestCallback callback, BiFunction<HttpResponse, Class<T>, T> responseCallback) {
        HttpClient client = getHttpClient();
        HttpGet httpGet = new HttpGet(url);
        HttpResponse response = null;
        try {
            if (callback != null) {
                callback.processRequest(client, httpGet);
            }
            response = client.execute(httpGet);
            if (responseCallback == null) {
                return calcResponse(response, clazz);
            }
            return responseCallback.apply(response, clazz);
        } catch (Exception e) {
            logger.error("执行GET请求错误: url={}", url, e);
            throw new SimpleRuntimeException(500, e.getMessage(), e);
        } finally {
            httpGet.releaseConnection();
            HttpClientUtils.closeQuietly(response);
        }
    }

    /**
     * 发送post请求
     *
     * @param url      请求地址
     * @param clazz    返回类型
     * @param callback 回调，构建请求
     * @param <T>
     * @return
     */
    public static <T> T sendHttpPost(String url, Class<T> clazz, HttpClientRequestCallback callback){
        return sendHttpPost(url, clazz, callback, null);
    }

    /**
     * 发送post请求
     *
     * @param url      请求地址
     * @param clazz    返回类型
     * @param callback 回调，构建请求
     * @param responseCallback 响应处理
     * @param <T>
     * @return
     */
    public static <T> T sendHttpPost(String url, Class<T> clazz, HttpClientRequestCallback callback, BiFunction<HttpResponse, Class<T>, T> responseCallback) {
        HttpClient client = getHttpClient();
        HttpPost httpPost = new HttpPost(url);
        HttpResponse response = null;
        try {
            if (callback != null) {
                callback.processRequest(client, httpPost);
            }
            response = client.execute(httpPost);
            if (responseCallback == null) {
                return calcResponse(response, clazz);
            }
            return responseCallback.apply(response, clazz);
        } catch (Exception e) {
            logger.error("执行POST请求错误", e);
        } finally {
            httpPost.releaseConnection();
            HttpClientUtils.closeQuietly(response);
        }
        return null;
    }

    /**
     * 返回类型定制
     *
     * @param response httpclient返回值
     * @param clazz    需要解析成的返回类型，如果不需要解析，可以用<code>HttpResponse</code>或者<code>HttpEntity</code>，自己解析内容
     * @param <T>
     * @return
     * @throws IOException
     */
    public static <T> T calcResponse(HttpResponse response, Class<T> clazz) throws IOException {
        HttpEntity httpEntity = response.getEntity();
        if (HttpResponse.class.isAssignableFrom(clazz)) {
            return (T) response;
        } else if (HttpEntity.class.isAssignableFrom(clazz)) {
            return (T) httpEntity;
        } else if (String.class.isAssignableFrom(clazz)) {
            String resultStr = EntityUtils.toString(httpEntity, StandardCharsets.UTF_8);
            logger.debug("RES: {}", resultStr);
            return (T) resultStr;
        } else {
            String resultStr = EntityUtils.toString(httpEntity, StandardCharsets.UTF_8);
            logger.debug("RES: {}", resultStr);
            return JsonUtils.fromJson(resultStr, clazz);
        }
    }

}
