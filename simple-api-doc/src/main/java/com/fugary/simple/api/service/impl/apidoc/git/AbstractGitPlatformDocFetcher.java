package com.fugary.simple.api.service.impl.apidoc.git;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.service.apidoc.asset.DocAssetStorageService;
import com.fugary.simple.api.service.apidoc.git.GitPlatformDocFetcher;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.http.SimpleHttpClientUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.git.GitRepoInfo;
import com.fugary.simple.api.web.vo.imports.BasicAuthVo;
import com.fugary.simple.api.web.vo.imports.UrlWithAuthVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * Git 平台文档抓取抽象基类，封装 HTTP 通信、平台认证、重试与资源转存等通用能力
 *
 * @author gary.fu
 */
@Slf4j
public abstract class AbstractGitPlatformDocFetcher implements GitPlatformDocFetcher {

    @Autowired(required = false)
    protected DocAssetStorageService docAssetStorageService;

    /**
     * 发送带有 Git 平台认证头的 HTTP GET 请求（支持网络抖动自动重试）
     */
    protected SimpleResult<String> sendGetRequest(String url, UrlWithAuthVo source, GitRepoInfo repoInfo) {
        return sendGetRequest(url, null, source, repoInfo);
    }

    /**
     * 发送带有 Git 平台认证头及自定义 Accept 头的 HTTP GET 请求（支持网络抖动自动重试）
     */
    protected SimpleResult<String> sendGetRequest(String url, String acceptHeader, UrlWithAuthVo source, GitRepoInfo repoInfo) {
        return SimpleHttpClientUtils.executeWithRetry(() -> doSendGetRequest(url, acceptHeader, source, repoInfo), "Git 请求 url=" + url);
    }

    /**
     * 发送带有 Git 平台认证头的 HTTP GET 请求获取二进制数据（支持网络抖动自动重试）
     */
    protected SimpleResult<byte[]> sendGetRequestBytes(String url, UrlWithAuthVo source, GitRepoInfo repoInfo) {
        return sendGetRequestBytes(url, null, source, repoInfo);
    }

    /**
     * 发送带有 Git 平台认证头及自定义 Accept 头的 HTTP GET 请求获取二进制数据（支持网络抖动自动重试）
     */
    protected SimpleResult<byte[]> sendGetRequestBytes(String url, String acceptHeader, UrlWithAuthVo source, GitRepoInfo repoInfo) {
        return SimpleHttpClientUtils.executeWithRetry(() -> doSendGetRequestBytes(url, acceptHeader, source, repoInfo), "Git 二进制请求 url=" + url);
    }

    /**
     * 执行单次带有 Git 平台认证头的 HTTP GET 请求
     */
    protected SimpleResult<String> doSendGetRequest(String url, UrlWithAuthVo source, GitRepoInfo repoInfo) {
        return doSendGetRequest(url, null, source, repoInfo);
    }

    /**
     * 执行单次带有 Git 平台认证头及自定义 Accept 头的 HTTP GET 请求
     */
    @SuppressWarnings("unchecked")
    protected SimpleResult<String> doSendGetRequest(String url, String acceptHeader, UrlWithAuthVo source, GitRepoInfo repoInfo) {
        Pair<String, HttpResponse> resultPair = null;
        try {
            resultPair = SimpleHttpClientUtils.sendHttpGet(url, Pair.class, (client, request) -> {
                request.setHeader("User-Agent", "Simple-Api-Doc");
                request.setHeader("Accept", StringUtils.defaultIfBlank(acceptHeader, "application/json, text/plain, */*"));
                processGitAuth(request, source, repoInfo);
            }, (httpResponse, clazz) -> {
                String resultStr = StringUtils.EMPTY;
                try {
                    HttpEntity entity = httpResponse.getEntity();
                    if (entity != null) {
                        resultStr = EntityUtils.toString(entity, StandardCharsets.UTF_8);
                    }
                } catch (Exception e) {
                    log.error("读取 HTTP 响应异常", e);
                }
                return Pair.of(resultStr, httpResponse);
            });
        } catch (Exception e) {
            log.error("Git HTTP 请求异常: url={}", url, e);
            String baseMsg = SimpleResultUtils.getErrorMsg(SystemErrorConstants.CODE_2009);
            return SimpleResultUtils.createError(SystemErrorConstants.CODE_2009, baseMsg + ": " + e.getMessage());
        }

        if (resultPair != null) {
            HttpResponse response = resultPair.getRight();
            if (response != null) {
                int status = response.getStatusLine().getStatusCode();
                if (status >= HttpStatus.SC_OK && status < HttpStatus.SC_MULTIPLE_CHOICES) {
                    return SimpleResultUtils.createSimpleResult(resultPair.getLeft());
                }
                String statusLine = response.getStatusLine().toString();
                log.error("Git API 请求失败: url={}, status={}, body={}", url, statusLine, resultPair.getLeft());
                String baseMsg = SimpleResultUtils.getErrorMsg(SystemErrorConstants.CODE_2009);
                if (status == HttpStatus.SC_NOT_FOUND || status == HttpStatus.SC_UNAUTHORIZED || status == HttpStatus.SC_FORBIDDEN) {
                    boolean hasAuth = source != null && !"none".equalsIgnoreCase(source.getAuthType()) && StringUtils.isNotBlank(source.getAuthContent());
                    if (!hasAuth) {
                        return SimpleResultUtils.createError(SystemErrorConstants.CODE_2009, "访问 Git 仓库失败(" + statusLine + ")，若为私有仓库请在【认证方式】中选择【Token认证】并填入 Token");
                    } else {
                        return SimpleResultUtils.createError(SystemErrorConstants.CODE_2009, "访问 Git 仓库失败(" + statusLine + ")，请检查 Token 权限或仓库路径/分支是否正确");
                    }
                }
                return SimpleResultUtils.createError(SystemErrorConstants.CODE_2009, baseMsg + ": " + statusLine);
            }
        }
        return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2009);
    }

    /**
     * 执行单次带有 Git 平台认证头的 HTTP GET 请求获取二进制数据（图片等）
     */
    protected SimpleResult<byte[]> doSendGetRequestBytes(String url, UrlWithAuthVo source, GitRepoInfo repoInfo) {
        return doSendGetRequestBytes(url, null, source, repoInfo);
    }

    /**
     * 执行单次带有 Git 平台认证头及自定义 Accept 头的 HTTP GET 请求获取二进制数据（图片等）
     */
    @SuppressWarnings("unchecked")
    protected SimpleResult<byte[]> doSendGetRequestBytes(String url, String acceptHeader, UrlWithAuthVo source, GitRepoInfo repoInfo) {
        Pair<byte[], HttpResponse> resultPair = null;
        try {
            resultPair = SimpleHttpClientUtils.sendHttpGet(url, Pair.class, (client, request) -> {
                request.setHeader("User-Agent", "Simple-Api-Doc");
                if (StringUtils.isNotBlank(acceptHeader)) {
                    request.setHeader("Accept", acceptHeader);
                }
                processGitAuth(request, source, repoInfo);
            }, (httpResponse, clazz) -> {
                byte[] bytes = new byte[0];
                try {
                    HttpEntity entity = httpResponse.getEntity();
                    if (entity != null) {
                        bytes = EntityUtils.toByteArray(entity);
                    }
                } catch (Exception e) {
                    log.error("读取 HTTP 响应字节异常", e);
                }
                return Pair.of(bytes, httpResponse);
            });
        } catch (Exception e) {
            log.error("Git HTTP 请求二进制异常: url={}", url, e);
            String baseMsg = SimpleResultUtils.getErrorMsg(SystemErrorConstants.CODE_2009);
            return SimpleResultUtils.createError(SystemErrorConstants.CODE_2009, baseMsg + ": " + e.getMessage());
        }

        if (resultPair != null) {
            HttpResponse response = resultPair.getRight();
            if (response != null) {
                int status = response.getStatusLine().getStatusCode();
                if (status >= HttpStatus.SC_OK && status < HttpStatus.SC_MULTIPLE_CHOICES) {
                    return SimpleResultUtils.createSimpleResult(resultPair.getLeft());
                }
                String statusLine = response.getStatusLine().toString();
                log.warn("Git API 请求二进制失败: url={}, status={}", url, statusLine);
                return SimpleResultUtils.createError(SystemErrorConstants.CODE_2009, "请求失败: " + statusLine);
            }
        }
        return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2009);
    }

    /**
     * 为 Git 请求添加平台认证头
     */
    protected void processGitAuth(HttpRequest request, UrlWithAuthVo source, GitRepoInfo repoInfo) {
        if (source == null || StringUtils.isBlank(source.getAuthType())) {
            return;
        }
        String authType = StringUtils.lowerCase(source.getAuthType());
        if (StringUtils.equalsIgnoreCase(authType, ApiDocConstants.AUTH_TYPE_BASIC)) {
            BasicAuthVo basicAuth = JsonUtils.fromJson(source.getAuthContent(), BasicAuthVo.class);
            if (basicAuth != null) {
                String token = Base64.getEncoder().encodeToString((basicAuth.getUserName() + ":" + basicAuth.getUserPassword()).getBytes());
                request.setHeader("Authorization", "Basic " + token);
            }
        } else if ("token".equalsIgnoreCase(authType) && StringUtils.isNotBlank(source.getAuthContent())) {
            String authContent = source.getAuthContent().trim();
            if (authContent.startsWith("{")) {
                try {
                    Map<String, Object> authMap = JsonUtils.fromJson(authContent, new TypeReference<>() {});
                    if (authMap != null) {
                        String token = Objects.toString(authMap.get("token"), "");
                        String headerName = Objects.toString(authMap.get("headerName"), "");
                        String prefix = Objects.toString(authMap.get("tokenPrefix"), "");

                        if (StringUtils.isBlank(headerName)) {
                            headerName = repoInfo != null && repoInfo.getPlatform() == GitRepoInfo.Platform.GITLAB ? "PRIVATE-TOKEN" : "Authorization";
                        }
                        if (StringUtils.isBlank(prefix) && "Authorization".equalsIgnoreCase(headerName)) {
                            prefix = "Bearer";
                        }
                        String headerValue = StringUtils.isNotBlank(prefix) ? prefix.trim() + " " + token.trim() : token.trim();
                        request.setHeader(headerName, headerValue);
                    }
                } catch (Exception e) {
                    log.warn("解析 Token 配置异常", e);
                }
            } else {
                // 纯文本 Token
                if (repoInfo != null && repoInfo.getPlatform() == GitRepoInfo.Platform.GITLAB) {
                    request.setHeader("PRIVATE-TOKEN", authContent);
                } else {
                    request.setHeader("Authorization", "Bearer " + authContent);
                }
            }
        }
    }

    /**
     * 判断文件路径是否为 Markdown 文件
     */
    public boolean isMarkdownFile(String path) {
        if (StringUtils.isBlank(path)) {
            return false;
        }
        String lower = path.toLowerCase();
        return lower.endsWith(".md") || lower.endsWith(".markdown");
    }

    /**
     * 剥离子路径前缀
     */
    public String stripSubPathPrefix(String path, String subPath) {
        if (StringUtils.isBlank(subPath) || StringUtils.isBlank(path)) {
            return path;
        }
        String cleanSubPath = subPath.replace('\\', '/');
        while (cleanSubPath.startsWith("/")) {
            cleanSubPath = cleanSubPath.substring(1);
        }
        while (cleanSubPath.endsWith("/")) {
            cleanSubPath = cleanSubPath.substring(0, cleanSubPath.length() - 1);
        }

        String cleanPath = path.replace('\\', '/');
        if (cleanPath.startsWith(cleanSubPath + "/")) {
            return cleanPath.substring(cleanSubPath.length() + 1);
        }
        if (cleanPath.equalsIgnoreCase(cleanSubPath)) {
            int lastSlash = cleanPath.lastIndexOf('/');
            return lastSlash >= 0 ? cleanPath.substring(lastSlash + 1) : cleanPath;
        }
        return cleanPath;
    }

    /**
     * 解析用于资产隔离的项目代号
     */
    protected String resolveProjectCode(GitRepoInfo repoInfo, UrlWithAuthVo source) {
        if (repoInfo != null && StringUtils.isNotBlank(repoInfo.getRepo())) {
            return repoInfo.getRepo();
        }
        return "default";
    }

    /**
     * 保存图片并记录到路径映射表中
     */
    protected void recordSavedImage(byte[] imageBytes, String filePath, String subPath, String projectCode, Map<String, String> imagePathToUrlMap) {
        if (docAssetStorageService != null && imageBytes != null && imageBytes.length > 0) {
            String localUrl = docAssetStorageService.saveImage(imageBytes, filePath, projectCode);
            if (StringUtils.isNotBlank(localUrl)) {
                String relativePath = stripSubPathPrefix(filePath, subPath);
                imagePathToUrlMap.put(relativePath, localUrl);
                imagePathToUrlMap.put(filePath, localUrl);
                imagePathToUrlMap.put(FilenameUtils.getName(filePath), localUrl);
            }
        }
    }

    /**
     * 处理 Markdown 正文并替换相对图片链接
     */
    protected String processMarkdownContent(String rawContent, String relativePath, Map<String, String> imagePathToUrlMap) {
        if (docAssetStorageService != null && !imagePathToUrlMap.isEmpty() && StringUtils.isNotBlank(rawContent)) {
            return docAssetStorageService.replaceRelativeImages(rawContent, relativePath, imagePathToUrlMap);
        }
        return rawContent;
    }
}
