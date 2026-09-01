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
 * Gitee 平台文档与资源抓取实现
 *
 * @author gary.fu
 */
@Slf4j
@Service
public class GiteeDocFetcherImpl extends AbstractGitPlatformDocFetcher {

    public static final String GITEE_API_BASE_URL = "https://gitee.com/api/v5";
    public static final String GITEE_RAW_BASE_URL = "https://gitee.com";

    @Override
    public boolean supports(GitRepoInfo.Platform platform) {
        return GitRepoInfo.Platform.GITEE == platform;
    }

    @Override
    @SuppressWarnings("unchecked")
    public SimpleResult<DocSourceData> fetchDocs(GitRepoInfo repoInfo, UrlWithAuthVo source) {
        String owner = repoInfo.getOwner();
        String repo = repoInfo.getRepo();
        String branch = StringUtils.defaultIfBlank(repoInfo.getBranch(), "master");
        String subPath = repoInfo.getSubPath();
        String projectCode = resolveProjectCode(repoInfo, source);

        String treeUrl = GITEE_API_BASE_URL + "/repos/" + owner + "/" + repo + "/git/trees/" + branch + "?recursive=1";
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
                        String rawImageUrl = GITEE_RAW_BASE_URL + "/" + owner + "/" + repo + "/raw/" + branch + "/" + filePath;
                        SimpleResult<byte[]> imageResult = sendGetRequestBytes(rawImageUrl, source, repoInfo);
                        if (imageResult.isSuccess()) {
                            recordSavedImage(imageResult.getResultData(), filePath, subPath, projectCode, imagePathToUrlMap);
                        }
                    }
                }
            }
        }

        List<Map<String, String>> docFiles = new ArrayList<>();
        for (String filePath : markdownFilePaths) {
            String rawFileUrl = GITEE_RAW_BASE_URL + "/" + owner + "/" + repo + "/raw/" + branch + "/" + filePath;
            SimpleResult<String> fileResult = sendGetRequest(rawFileUrl, source, repoInfo);
            if (fileResult.isSuccess()) {
                String relativePath = stripSubPathPrefix(filePath, subPath);
                String content = processMarkdownContent(fileResult.getResultData(), relativePath, imagePathToUrlMap);
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
}
