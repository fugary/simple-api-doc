package com.fugary.simple.api.service.impl.apidoc.git;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.service.apidoc.asset.DocAssetStorageService;
import com.fugary.simple.api.service.apidoc.git.GitDocContentProvider;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.http.SimpleHttpClientUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.git.GitRepoInfo;
import com.fugary.simple.api.web.vo.imports.DocSourceData;
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
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Git 仓库目录文档抓取实现（支持 GitLab / GitHub / Gitee）
 *
 * @author gary.fu
 */
@Slf4j
@Service
public class GitDocContentProviderImpl implements GitDocContentProvider {

    @Autowired(required = false)
    private DocAssetStorageService docAssetStorageService;

    @Override
    public SimpleResult<DocSourceData> getContent(GitRepoInfo repoInfo, UrlWithAuthVo source) {
        if (repoInfo == null || repoInfo.getPlatform() == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2009);
        }

        try {
            switch (repoInfo.getPlatform()) {
                case GITLAB:
                    return fetchGitLabDocs(repoInfo, source);
                case GITHUB:
                    return fetchGitHubDocs(repoInfo, source);
                case GITEE:
                    return fetchGiteeDocs(repoInfo, source);
                default:
                    return SimpleResultUtils.createError(SystemErrorConstants.CODE_2009, "暂不支持的 Git 服务平台");
            }
        } catch (Exception e) {
            log.error("Git 仓库文档抓取异常: repoInfo={}", repoInfo, e);
            String baseMsg = SimpleResultUtils.getErrorMsg(SystemErrorConstants.CODE_2009);
            return SimpleResultUtils.createError(SystemErrorConstants.CODE_2009, baseMsg + ": " + e.getMessage());
        }
    }

    /**
     * 从 GitLab API 拉取指定目录下的 Markdown 文档及相关图片
     */
    protected SimpleResult<DocSourceData> fetchGitLabDocs(GitRepoInfo repoInfo, UrlWithAuthVo source) {
        String serverUrl = repoInfo.getServerUrl();
        String projectPath = repoInfo.getProjectPath();
        String branch = StringUtils.defaultIfBlank(repoInfo.getBranch(), "main");
        String subPath = repoInfo.getSubPath();
        String projectCode = resolveProjectCode(repoInfo, source);

        String encodedProject = URLEncoder.encode(projectPath, StandardCharsets.UTF_8);
        StringBuilder treeUrlBuilder = new StringBuilder(serverUrl)
                .append("/api/v4/projects/")
                .append(encodedProject)
                .append("/repository/tree?recursive=true&per_page=100&ref=")
                .append(URLEncoder.encode(branch, StandardCharsets.UTF_8));
        if (StringUtils.isNotBlank(subPath)) {
            treeUrlBuilder.append("&path=").append(URLEncoder.encode(subPath, StandardCharsets.UTF_8));
        }

        String treeUrl = treeUrlBuilder.toString();
        log.info("GitLab 获取目录树: url={}", treeUrl);

        SimpleResult<String> treeResult = sendGetRequest(treeUrl, source, repoInfo);
        if (!treeResult.isSuccess()) {
            return SimpleResultUtils.createError(treeResult.getCode(), treeResult.getMessage());
        }

        List<Map<String, Object>> treeItems = JsonUtils.fromJson(treeResult.getResultData(), new TypeReference<>() {});
        if (treeItems == null || treeItems.isEmpty()) {
            return SimpleResultUtils.createError(SystemErrorConstants.CODE_2009, "GitLab 目标目录下未找到任何文件: " + subPath);
        }

        // 1. 先抓取同目录/子目录下的所有图片并转存本地
        Map<String, String> imagePathToUrlMap = new HashMap<>();
        List<String> markdownFilePaths = new ArrayList<>();

        for (Map<String, Object> item : treeItems) {
            String type = Objects.toString(item.get("type"), "");
            String filePath = Objects.toString(item.get("path"), "");
            if ("blob".equalsIgnoreCase(type)) {
                if (isMarkdownFile(filePath)) {
                    markdownFilePaths.add(filePath);
                } else if (docAssetStorageService != null && docAssetStorageService.isImageFile(filePath)) {
                    String rawImageUrl = serverUrl + "/api/v4/projects/" + encodedProject
                            + "/repository/files/" + URLEncoder.encode(filePath, StandardCharsets.UTF_8)
                            + "/raw?ref=" + URLEncoder.encode(branch, StandardCharsets.UTF_8);
                    SimpleResult<byte[]> imageResult = sendGetRequestBytes(rawImageUrl, source, repoInfo);
                    if (imageResult.isSuccess() && imageResult.getResultData() != null) {
                        String localUrl = docAssetStorageService.saveImage(imageResult.getResultData(), filePath, projectCode);
                        if (StringUtils.isNotBlank(localUrl)) {
                            String relativePath = stripSubPathPrefix(filePath, subPath);
                            imagePathToUrlMap.put(relativePath, localUrl);
                            imagePathToUrlMap.put(filePath, localUrl);
                            imagePathToUrlMap.put(FilenameUtils.getName(filePath), localUrl);
                        }
                    }
                }
            }
        }

        // 2. 抓取 Markdown 文件并替换相对图片链接
        List<Map<String, String>> docFiles = new ArrayList<>();
        for (String filePath : markdownFilePaths) {
            String rawFileUrl = serverUrl + "/api/v4/projects/" + encodedProject
                    + "/repository/files/" + URLEncoder.encode(filePath, StandardCharsets.UTF_8)
                    + "/raw?ref=" + URLEncoder.encode(branch, StandardCharsets.UTF_8);

            SimpleResult<String> fileResult = sendGetRequest(rawFileUrl, source, repoInfo);
            if (fileResult.isSuccess()) {
                String relativePath = stripSubPathPrefix(filePath, subPath);
                String content = fileResult.getResultData();
                if (docAssetStorageService != null && !imagePathToUrlMap.isEmpty()) {
                    content = docAssetStorageService.replaceRelativeImages(content, relativePath, imagePathToUrlMap);
                }
                Map<String, String> fileMap = new LinkedHashMap<>();
                fileMap.put("path", relativePath);
                fileMap.put("content", content);
                docFiles.add(fileMap);
            } else {
                log.warn("GitLab 拉取单个文件失败: path={}, url={}", filePath, rawFileUrl);
            }
        }

        if (docFiles.isEmpty()) {
            return SimpleResultUtils.createError(SystemErrorConstants.CODE_2009, "GitLab 目标目录下未找到 Markdown 文档 (*.md)");
        }

        return SimpleResultUtils.createSimpleResult(DocSourceData.ofText(JsonUtils.toJson(docFiles)));
    }

    /**
     * 从 GitHub API 拉取指定目录下的 Markdown 文档及相关图片
     */
    protected SimpleResult<DocSourceData> fetchGitHubDocs(GitRepoInfo repoInfo, UrlWithAuthVo source) {
        String owner = repoInfo.getOwner();
        String repo = repoInfo.getRepo();
        String branch = StringUtils.defaultIfBlank(repoInfo.getBranch(), "main");
        String subPath = repoInfo.getSubPath();
        String projectCode = resolveProjectCode(repoInfo, source);

        String treeUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/git/trees/" + branch + "?recursive=1";
        log.info("GitHub 获取目录树: url={}", treeUrl);

        SimpleResult<String> treeResult = sendGetRequest(treeUrl, source, repoInfo);
        if (!treeResult.isSuccess()) {
            return SimpleResultUtils.createError(treeResult.getCode(), treeResult.getMessage());
        }

        Map<String, Object> treeResponse = JsonUtils.fromJson(treeResult.getResultData(), new TypeReference<>() {});
        if (treeResponse == null || !treeResponse.containsKey("tree")) {
            return SimpleResultUtils.createError(SystemErrorConstants.CODE_2009, "GitHub 目录树解析失败");
        }

        List<Map<String, Object>> treeItems = (List<Map<String, Object>>) treeResponse.get("tree");
        if (treeItems == null || treeItems.isEmpty()) {
            return SimpleResultUtils.createError(SystemErrorConstants.CODE_2009, "GitHub 仓库为空或未找到文件");
        }

        Map<String, String> imagePathToUrlMap = new HashMap<>();
        List<String> markdownFilePaths = new ArrayList<>();
        String normalizedSubPath = subPath.replace('\\', '/');

        // 1. 抓取图片并转存
        for (Map<String, Object> item : treeItems) {
            String type = Objects.toString(item.get("type"), "");
            String filePath = Objects.toString(item.get("path"), "");
            if ("blob".equalsIgnoreCase(type)) {
                if (StringUtils.isBlank(normalizedSubPath) || filePath.startsWith(normalizedSubPath + "/") || filePath.equalsIgnoreCase(normalizedSubPath)) {
                    if (isMarkdownFile(filePath)) {
                        markdownFilePaths.add(filePath);
                    } else if (docAssetStorageService != null && docAssetStorageService.isImageFile(filePath)) {
                        String rawImageUrl = "https://raw.githubusercontent.com/" + owner + "/" + repo + "/" + branch + "/" + filePath;
                        SimpleResult<byte[]> imageResult = sendGetRequestBytes(rawImageUrl, source, repoInfo);
                        if (imageResult.isSuccess() && imageResult.getResultData() != null) {
                            String localUrl = docAssetStorageService.saveImage(imageResult.getResultData(), filePath, projectCode);
                            if (StringUtils.isNotBlank(localUrl)) {
                                String relativePath = stripSubPathPrefix(filePath, subPath);
                                imagePathToUrlMap.put(relativePath, localUrl);
                                imagePathToUrlMap.put(filePath, localUrl);
                                imagePathToUrlMap.put(FilenameUtils.getName(filePath), localUrl);
                            }
                        }
                    }
                }
            }
        }

        // 2. 抓取 Markdown 并替换图片链接
        List<Map<String, String>> docFiles = new ArrayList<>();
        for (String filePath : markdownFilePaths) {
            String rawFileUrl = "https://raw.githubusercontent.com/" + owner + "/" + repo + "/" + branch + "/" + filePath;
            SimpleResult<String> fileResult = sendGetRequest(rawFileUrl, source, repoInfo);
            if (fileResult.isSuccess()) {
                String relativePath = stripSubPathPrefix(filePath, subPath);
                String content = fileResult.getResultData();
                if (docAssetStorageService != null && !imagePathToUrlMap.isEmpty()) {
                    content = docAssetStorageService.replaceRelativeImages(content, relativePath, imagePathToUrlMap);
                }
                Map<String, String> fileMap = new LinkedHashMap<>();
                fileMap.put("path", relativePath);
                fileMap.put("content", content);
                docFiles.add(fileMap);
            } else {
                log.warn("GitHub 拉取单个文件失败: path={}, url={}", filePath, rawFileUrl);
            }
        }

        if (docFiles.isEmpty()) {
            return SimpleResultUtils.createError(SystemErrorConstants.CODE_2009, "GitHub 目标目录下未找到 Markdown 文档 (*.md): " + subPath);
        }

        return SimpleResultUtils.createSimpleResult(DocSourceData.ofText(JsonUtils.toJson(docFiles)));
    }

    /**
     * 从 Gitee API 拉取指定目录下的 Markdown 文档
     */
    protected SimpleResult<DocSourceData> fetchGiteeDocs(GitRepoInfo repoInfo, UrlWithAuthVo source) {
        String owner = repoInfo.getOwner();
        String repo = repoInfo.getRepo();
        String branch = StringUtils.defaultIfBlank(repoInfo.getBranch(), "master");
        String subPath = repoInfo.getSubPath();
        String projectCode = resolveProjectCode(repoInfo, source);

        String treeUrl = "https://gitee.com/api/v5/repos/" + owner + "/" + repo + "/git/trees/" + branch + "?recursive=1";
        log.info("Gitee 获取目录树: url={}", treeUrl);

        SimpleResult<String> treeResult = sendGetRequest(treeUrl, source, repoInfo);
        if (!treeResult.isSuccess()) {
            return SimpleResultUtils.createError(treeResult.getCode(), treeResult.getMessage());
        }

        Map<String, Object> treeResponse = JsonUtils.fromJson(treeResult.getResultData(), new TypeReference<>() {});
        List<Map<String, Object>> treeItems = treeResponse != null ? (List<Map<String, Object>>) treeResponse.get("tree") : null;
        if (treeItems == null || treeItems.isEmpty()) {
            return SimpleResultUtils.createError(SystemErrorConstants.CODE_2009, "Gitee 仓库为空或未找到文件");
        }

        Map<String, String> imagePathToUrlMap = new HashMap<>();
        List<String> markdownFilePaths = new ArrayList<>();
        String normalizedSubPath = subPath.replace('\\', '/');

        for (Map<String, Object> item : treeItems) {
            String type = Objects.toString(item.get("type"), "");
            String filePath = Objects.toString(item.get("path"), "");
            if ("blob".equalsIgnoreCase(type)) {
                if (StringUtils.isBlank(normalizedSubPath) || filePath.startsWith(normalizedSubPath + "/") || filePath.equalsIgnoreCase(normalizedSubPath)) {
                    if (isMarkdownFile(filePath)) {
                        markdownFilePaths.add(filePath);
                    } else if (docAssetStorageService != null && docAssetStorageService.isImageFile(filePath)) {
                        String rawImageUrl = "https://gitee.com/" + owner + "/" + repo + "/raw/" + branch + "/" + filePath;
                        SimpleResult<byte[]> imageResult = sendGetRequestBytes(rawImageUrl, source, repoInfo);
                        if (imageResult.isSuccess() && imageResult.getResultData() != null) {
                            String localUrl = docAssetStorageService.saveImage(imageResult.getResultData(), filePath, projectCode);
                            if (StringUtils.isNotBlank(localUrl)) {
                                String relativePath = stripSubPathPrefix(filePath, subPath);
                                imagePathToUrlMap.put(relativePath, localUrl);
                                imagePathToUrlMap.put(filePath, localUrl);
                                imagePathToUrlMap.put(FilenameUtils.getName(filePath), localUrl);
                            }
                        }
                    }
                }
            }
        }

        List<Map<String, String>> docFiles = new ArrayList<>();
        for (String filePath : markdownFilePaths) {
            String rawFileUrl = "https://gitee.com/" + owner + "/" + repo + "/raw/" + branch + "/" + filePath;
            SimpleResult<String> fileResult = sendGetRequest(rawFileUrl, source, repoInfo);
            if (fileResult.isSuccess()) {
                String relativePath = stripSubPathPrefix(filePath, subPath);
                String content = fileResult.getResultData();
                if (docAssetStorageService != null && !imagePathToUrlMap.isEmpty()) {
                    content = docAssetStorageService.replaceRelativeImages(content, relativePath, imagePathToUrlMap);
                }
                Map<String, String> fileMap = new LinkedHashMap<>();
                fileMap.put("path", relativePath);
                fileMap.put("content", content);
                docFiles.add(fileMap);
            }
        }

        if (docFiles.isEmpty()) {
            return SimpleResultUtils.createError(SystemErrorConstants.CODE_2009, "Gitee 目标目录下未找到 Markdown 文档 (*.md)");
        }

        return SimpleResultUtils.createSimpleResult(DocSourceData.ofText(JsonUtils.toJson(docFiles)));
    }

    protected String resolveProjectCode(GitRepoInfo repoInfo, UrlWithAuthVo source) {
        if (repoInfo != null && StringUtils.isNotBlank(repoInfo.getRepo())) {
            return repoInfo.getRepo();
        }
        return "default";
    }

    /**
     * 发送带有 Git 平台认证头的 HTTP GET 请求（支持网络抖动自动重试）
     */
    protected SimpleResult<String> sendGetRequest(String url, UrlWithAuthVo source, GitRepoInfo repoInfo) {
        return SimpleHttpClientUtils.executeWithRetry(() -> doSendGetRequest(url, source, repoInfo), "Git 请求 url=" + url);
    }

    /**
     * 发送带有 Git 平台认证头的 HTTP GET 请求获取二进制数据（支持网络抖动自动重试）
     */
    protected SimpleResult<byte[]> sendGetRequestBytes(String url, UrlWithAuthVo source, GitRepoInfo repoInfo) {
        return SimpleHttpClientUtils.executeWithRetry(() -> doSendGetRequestBytes(url, source, repoInfo), "Git 二进制请求 url=" + url);
    }

    /**
     * 执行单次带有 Git 平台认证头的 HTTP GET 请求
     */
    protected SimpleResult<String> doSendGetRequest(String url, UrlWithAuthVo source, GitRepoInfo repoInfo) {
        Pair<String, HttpResponse> resultPair = null;
        try {
            resultPair = SimpleHttpClientUtils.sendHttpGet(url, Pair.class, (client, request) -> {
                request.setHeader("User-Agent", "Simple-Api-Doc");
                request.setHeader("Accept", "application/json, text/plain, */*");
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
        Pair<byte[], HttpResponse> resultPair = null;
        try {
            resultPair = SimpleHttpClientUtils.sendHttpGet(url, Pair.class, (client, request) -> {
                request.setHeader("User-Agent", "Simple-Api-Doc");
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

    protected boolean isMarkdownFile(String path) {
        if (StringUtils.isBlank(path)) {
            return false;
        }
        String lower = path.toLowerCase();
        return lower.endsWith(".md") || lower.endsWith(".markdown");
    }

    protected String stripSubPathPrefix(String path, String subPath) {
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
}
