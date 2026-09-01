package com.fugary.simple.api.service.impl.apidoc.git;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.git.GitRepoInfo;
import com.fugary.simple.api.web.vo.imports.DocSourceData;
import com.fugary.simple.api.web.vo.imports.UrlWithAuthVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * GitHub 平台文档与资源抓取实现（支持 Git Trees 递归与 Contents API 降级）
 *
 * @author gary.fu
 */
@Slf4j
@Service
public class GitHubDocFetcherImpl extends AbstractGitPlatformDocFetcher {

    public static final String GITHUB_API_BASE_URL = "https://api.github.com";
    public static final String GITHUB_RAW_BASE_URL = "https://raw.githubusercontent.com";
    public static final String GITHUB_RAW_MEDIA_TYPE = "application/vnd.github.raw";

    @Override
    public boolean supports(GitRepoInfo.Platform platform) {
        return GitRepoInfo.Platform.GITHUB == platform;
    }

    @Override
    @SuppressWarnings("unchecked")
    public SimpleResult<DocSourceData> fetchDocs(GitRepoInfo repoInfo, UrlWithAuthVo source) {
        String owner = repoInfo.getOwner();
        String repo = repoInfo.getRepo();
        String branch = StringUtils.defaultIfBlank(repoInfo.getBranch(), "main");
        String subPath = repoInfo.getSubPath();
        String projectCode = resolveProjectCode(repoInfo, source);

        String treeUrl = GITHUB_API_BASE_URL + "/repos/" + owner + "/" + repo + "/git/trees/" + branch + "?recursive=1";
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
                        String rawImageUrl = GITHUB_RAW_BASE_URL + "/" + owner + "/" + repo + "/" + branch + "/" + filePath;
                        SimpleResult<byte[]> imageResult = sendGetRequestBytes(rawImageUrl, source, repoInfo);
                        if (!imageResult.isSuccess()) {
                            // 降级尝试 GitHub Contents API
                            String apiFileUrl = GITHUB_API_BASE_URL + "/repos/" + owner + "/" + repo + "/contents/" + filePath + "?ref=" + branch;
                            imageResult = sendGetRequestBytes(apiFileUrl, GITHUB_RAW_MEDIA_TYPE, source, repoInfo);
                        }
                        if (imageResult.isSuccess()) {
                            recordSavedImage(imageResult.getResultData(), filePath, subPath, projectCode, imagePathToUrlMap);
                        }
                    }
                }
            }
        }

        // 2. 抓取 Markdown 并替换图片链接
        List<Map<String, String>> docFiles = new ArrayList<>();
        for (String filePath : markdownFilePaths) {
            String rawFileUrl = GITHUB_RAW_BASE_URL + "/" + owner + "/" + repo + "/" + branch + "/" + filePath;
            SimpleResult<String> fileResult = sendGetRequest(rawFileUrl, source, repoInfo);
            if (!fileResult.isSuccess()) {
                // 降级尝试 GitHub Contents API 获取 Raw 文本
                String apiFileUrl = GITHUB_API_BASE_URL + "/repos/" + owner + "/" + repo + "/contents/" + filePath + "?ref=" + branch;
                fileResult = sendGetRequest(apiFileUrl, GITHUB_RAW_MEDIA_TYPE, source, repoInfo);
            }
            if (fileResult.isSuccess()) {
                String relativePath = stripSubPathPrefix(filePath, subPath);
                String content = processMarkdownContent(fileResult.getResultData(), relativePath, imagePathToUrlMap);
                Map<String, String> fileMap = new LinkedHashMap<>();
                fileMap.put("path", relativePath);
                fileMap.put("content", content);
                docFiles.add(fileMap);
            } else {
                log.warn("GitHub 拉取单个文件失败: path={}, url={}, error={}", filePath, rawFileUrl, fileResult.getMessage());
            }
        }

        if (docFiles.isEmpty()) {
            return SimpleResultUtils.createError(SystemErrorConstants.CODE_2009, "GitHub 目标目录下未找到 Markdown 文档 (*.md): " + subPath);
        }

        return SimpleResultUtils.createSimpleResult(DocSourceData.ofText(JsonUtils.toJson(docFiles)));
    }
}
