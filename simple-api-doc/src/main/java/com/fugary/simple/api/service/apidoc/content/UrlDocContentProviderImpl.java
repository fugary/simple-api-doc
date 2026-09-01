package com.fugary.simple.api.service.apidoc.content;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.exception.SimpleRuntimeException;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.http.SimpleHttpClientUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.imports.DocSourceData;
import com.fugary.simple.api.web.vo.imports.BasicAuthVo;
import com.fugary.simple.api.web.vo.imports.UrlWithAuthVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.util.EntityUtils;
import com.fugary.simple.api.service.apidoc.git.GitDocContentProvider;
import com.fugary.simple.api.utils.git.GitRepoUrlResolver;
import com.fugary.simple.api.web.vo.git.GitRepoInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
@Slf4j
@Primary
@Component
public class UrlDocContentProviderImpl implements DocContentProvider<UrlWithAuthVo> {

    @Autowired(required = false)
    private GitDocContentProvider gitDocContentProvider;

    @Override
    public SimpleResult<DocSourceData> getContent(UrlWithAuthVo source) {
        if (source == null || StringUtils.isBlank(source.getUrl())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2009);
        }

        // 1. 优先尝试 Git 仓库目录 URL 智能解析
        GitRepoInfo gitRepoInfo = GitRepoUrlResolver.resolve(source.getUrl());
        if (gitRepoInfo != null && gitDocContentProvider != null) {
            log.info("智能嗅探命中 Git 目录: cloneUrl={}, subPath={}", gitRepoInfo.getCloneUrl(), gitRepoInfo.getSubPath());
            return gitDocContentProvider.getContent(gitRepoInfo, source);
        }

        // 2. 普通 HTTP URL 下载（支持网络抖动自动重试）
        return SimpleHttpClientUtils.executeWithRetry(() -> doFetchContent(source), "URL下载 url=" + source.getUrl());
    }

    /**
     * 单次 HTTP URL 下载
     */
    protected SimpleResult<DocSourceData> doFetchContent(UrlWithAuthVo source) {
        Pair<DocSourceData, HttpResponse> resultPair = null;
        try {
            resultPair = SimpleHttpClientUtils.sendHttpGet(source.getUrl(), Pair.class, (client, request) -> {
                log.info("client = {}, request = {}", client, request);
                processAuth(request, source);
            }, (httpResponse, clazz) -> {
                DocSourceData sourceData = null;
                try {
                    HttpEntity entity = httpResponse.getEntity();
                    if (entity != null) {
                        byte[] bytes = EntityUtils.toByteArray(entity);
                        String fileName = resolveFileName(source.getUrl());
                        if (isZipContent(bytes, httpResponse, source.getUrl())) {
                            sourceData = DocSourceData.ofBinary(bytes, fileName, "application/zip");
                        } else {
                            sourceData = DocSourceData.ofText(new String(bytes, StandardCharsets.UTF_8), fileName);
                        }
                    }
                } catch (IOException e) {
                    log.error("Url数据解析错误", e);
                }
                return Pair.of(sourceData, httpResponse);
            });
        } catch (SimpleRuntimeException e) {
            log.error("URL数据下载异常: url={}", source.getUrl(), e);
            String baseMsg = SimpleResultUtils.getErrorMsg(SystemErrorConstants.CODE_2009);
            String detail = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            String errorMsg = StringUtils.isNotBlank(detail) ? baseMsg + ": " + detail : baseMsg;
            return SimpleResultUtils.createError(SystemErrorConstants.CODE_2009, errorMsg);
        }
        if (resultPair != null) {
            HttpResponse httpResponse = resultPair.getRight();
            if (httpResponse != null) {
                int statusCode = httpResponse.getStatusLine().getStatusCode();
                if (HttpStatus.SC_OK == statusCode) {
                    return SimpleResultUtils.createSimpleResult(resultPair.getLeft());
                }
                String statusLineStr = httpResponse.getStatusLine().toString();
                log.error("URL数据下载失败: url={}, status={}", source.getUrl(), statusLineStr);
                String baseMsg = SimpleResultUtils.getErrorMsg(SystemErrorConstants.CODE_2009);
                return SimpleResultUtils.createError(SystemErrorConstants.CODE_2009, baseMsg + ": " + statusLineStr);
            }
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2005);
        }
        return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2009);
    }

    protected String resolveFileName(String url) {
        if (StringUtils.isBlank(url)) {
            return null;
        }
        String cleanUrl = StringUtils.substringBefore(url, "?");
        int lastSlash = cleanUrl.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < cleanUrl.length() - 1) {
            return cleanUrl.substring(lastSlash + 1);
        }
        return null;
    }

    /**
     * 判断是否为 ZIP 压缩包内容
     *
     * @param bytes 下载的二进制字节
     * @param response HTTP 响应
     * @param url 请求 URL
     * @return 是否为 ZIP 文件
     */
    protected boolean isZipContent(byte[] bytes, HttpResponse response, String url) {
        if (bytes == null || bytes.length < 4) {
            return false;
        }
        // 1. ZIP 文件 PK 魔数特征（0x50, 0x4B）
        if (bytes[0] == 0x50 && bytes[1] == 0x4B) {
            return true;
        }
        // 2. Content-Type 响应头特征
        if (response != null && response.getEntity() != null) {
            Header contentType = response.getEntity().getContentType();
            if (contentType != null && StringUtils.isNotBlank(contentType.getValue())) {
                String ct = contentType.getValue().toLowerCase();
                if (ct.contains("application/zip") || ct.contains("application/x-zip-compressed")) {
                    return true;
                }
            }
        }
        // 3. URL 后缀特征
        if (StringUtils.isNotBlank(url)) {
            String urlPath = StringUtils.substringBefore(url, "?").toLowerCase();
            return urlPath.endsWith(".zip");
        }
        return false;
    }

    /**
     * 处理认证配置（Basic 认证 / Token 认证）
     *
     * @param request HTTP 请求
     * @param source 认证配置
     */
    protected void processAuth(HttpRequest request, UrlWithAuthVo source) {
        if (source == null || StringUtils.isBlank(source.getAuthType())) {
            return;
        }
        String authType = StringUtils.lowerCase(source.getAuthType());
        if (StringUtils.equalsIgnoreCase(authType, ApiDocConstants.AUTH_TYPE_BASIC)) {
            String basicHeader = getBasicHeader(source);
            if (StringUtils.isNotBlank(basicHeader)) {
                request.setHeader("Authorization", basicHeader);
            }
        } else if ("token".equalsIgnoreCase(authType)) {
            processTokenAuth(request, source);
        }
    }

    /**
     * 处理 Token 认证配置
     *
     * @param request HTTP 请求
     * @param source 认证配置
     */
    protected void processTokenAuth(HttpRequest request, UrlWithAuthVo source) {
        if (StringUtils.isBlank(source.getAuthContent())) {
            return;
        }
        String authContent = source.getAuthContent().trim();
        if (authContent.startsWith("{")) {
            try {
                Map<String, Object> authMap = JsonUtils.fromJson(authContent, new TypeReference<>() {});
                if (authMap != null) {
                    String token = Objects.toString(authMap.get("token"), "");
                    if (StringUtils.isNotBlank(token)) {
                        String headerName = Objects.toString(authMap.get("headerName"), "Authorization");
                        String prefix = Objects.toString(authMap.get("tokenPrefix"), "");
                        String headerValue = StringUtils.isNotBlank(prefix) ? prefix.trim() + " " + token.trim() : token.trim();
                        request.setHeader(headerName, headerValue);
                    }
                }
            } catch (Exception e) {
                log.warn("解析 Token Auth 配置异常", e);
            }
        } else {
            request.setHeader("Authorization", "Bearer " + authContent);
        }
    }

    protected String getBasicHeader(UrlWithAuthVo source) {
        BasicAuthVo basicAuth = JsonUtils.fromJson(source.getAuthContent(), BasicAuthVo.class);
        if (basicAuth != null) {
            return String.format(ApiDocConstants.AUTH_TYPE_BASIC + " %s", Base64.getEncoder().encodeToString((basicAuth.getUserName() + ":" + basicAuth.getUserPassword()).getBytes()));
        }
        return null;
    }
}

