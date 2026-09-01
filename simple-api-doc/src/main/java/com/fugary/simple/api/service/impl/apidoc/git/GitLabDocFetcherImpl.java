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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * GitLab 平台文档与资源抓取实现（支持 API V4 多页循环拉取）
 *
 * @author gary.fu
 */
@Slf4j
@Service
public class GitLabDocFetcherImpl extends AbstractGitPlatformDocFetcher {

    public static final String GITLAB_API_V4_PREFIX = "/api/v4/projects/";
    public static final int GITLAB_PER_PAGE = 100;
    public static final int MAX_PAGE_LIMIT = 500;

    @Override
    public boolean supports(GitRepoInfo.Platform platform) {
        return GitRepoInfo.Platform.GITLAB == platform;
    }

    @Override
    public SimpleResult<DocSourceData> fetchDocs(GitRepoInfo repoInfo, UrlWithAuthVo source) {
        String serverUrl = repoInfo.getServerUrl();
        String projectPath = repoInfo.getProjectPath();
        String branch = StringUtils.defaultIfBlank(repoInfo.getBranch(), "main");
        String subPath = repoInfo.getSubPath();
        String projectCode = resolveProjectCode(repoInfo, source);

        String encodedProject = URLEncoder.encode(projectPath, StandardCharsets.UTF_8);
        StringBuilder baseTreeUrlBuilder = new StringBuilder(serverUrl)
                .append(GITLAB_API_V4_PREFIX)
                .append(encodedProject)
                .append("/repository/tree?recursive=true&per_page=")
                .append(GITLAB_PER_PAGE)
                .append("&ref=")
                .append(URLEncoder.encode(branch, StandardCharsets.UTF_8));
        if (StringUtils.isNotBlank(subPath)) {
            baseTreeUrlBuilder.append("&path=").append(URLEncoder.encode(subPath, StandardCharsets.UTF_8));
        }

        String baseTreeUrl = baseTreeUrlBuilder.toString();
        List<Map<String, Object>> allTreeItems = new ArrayList<>();
        int page = 1;

        // 分页循环拉取，直至全部获取完毕（解决单页 per_page=100 限制）
        while (page <= MAX_PAGE_LIMIT) {
            String pageTreeUrl = baseTreeUrl + "&page=" + page;
            log.info("GitLab 获取目录树(page={}): url={}", page, pageTreeUrl);

            SimpleResult<String> treeResult = sendGetRequest(pageTreeUrl, source, repoInfo);
            if (!treeResult.isSuccess()) {
                if (page == 1) {
                    return SimpleResultUtils.createError(treeResult.getCode(), treeResult.getMessage());
                }
                log.warn("GitLab 分页获取目录树中断: page={}, error={}", page, treeResult.getMessage());
                break;
            }

            List<Map<String, Object>> pageItems = JsonUtils.fromJson(treeResult.getResultData(), new TypeReference<>() {});
            if (pageItems == null || pageItems.isEmpty()) {
                break;
            }
            allTreeItems.addAll(pageItems);
            if (pageItems.size() < GITLAB_PER_PAGE) {
                break;
            }
            page++;
        }

        if (allTreeItems.isEmpty()) {
            return SimpleResultUtils.createError(SystemErrorConstants.CODE_2009, "GitLab 目标目录下未找到任何文件: " + subPath);
        }

        // 1. 先抓取同目录/子目录下的所有图片并转存本地
        Map<String, String> imagePathToUrlMap = new HashMap<>();
        List<String> markdownFilePaths = new ArrayList<>();

        for (Map<String, Object> item : allTreeItems) {
            String type = Objects.toString(item.get("type"), "");
            String filePath = Objects.toString(item.get("path"), "");
            if ("blob".equalsIgnoreCase(type)) {
                if (isMarkdownFile(filePath)) {
                    markdownFilePaths.add(filePath);
                } else if (docAssetStorageService != null && docAssetStorageService.isImageFile(filePath)) {
                    String rawImageUrl = serverUrl + GITLAB_API_V4_PREFIX + encodedProject
                            + "/repository/files/" + URLEncoder.encode(filePath, StandardCharsets.UTF_8)
                            + "/raw?ref=" + URLEncoder.encode(branch, StandardCharsets.UTF_8);
                    SimpleResult<byte[]> imageResult = sendGetRequestBytes(rawImageUrl, source, repoInfo);
                    if (imageResult.isSuccess()) {
                        recordSavedImage(imageResult.getResultData(), filePath, subPath, projectCode, imagePathToUrlMap);
                    }
                }
            }
        }

        // 2. 抓取 Markdown 文件并替换相对图片链接
        List<Map<String, String>> docFiles = new ArrayList<>();
        for (String filePath : markdownFilePaths) {
            String rawFileUrl = serverUrl + GITLAB_API_V4_PREFIX + encodedProject
                    + "/repository/files/" + URLEncoder.encode(filePath, StandardCharsets.UTF_8)
                    + "/raw?ref=" + URLEncoder.encode(branch, StandardCharsets.UTF_8);

            SimpleResult<String> fileResult = sendGetRequest(rawFileUrl, source, repoInfo);
            if (fileResult.isSuccess()) {
                String relativePath = stripSubPathPrefix(filePath, subPath);
                String content = processMarkdownContent(fileResult.getResultData(), relativePath, imagePathToUrlMap);
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
}
