package com.fugary.simple.api.utils.git;

import com.fugary.simple.api.web.vo.git.GitRepoInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Git 仓库与目录树 URL 智能通用解析器（全网通用 Web 目录树与 Git Clone 链接智能嗅探）
 *
 * @author gary.fu
 */
@Slf4j
public class GitRepoUrlResolver {

    /**
     * 全网通用 Git Web 目录树规范匹配：
     * 一条正则统一覆盖 /tree/、/-/tree/、/src/、/src/branch/、/src/tag/、/src/commit/ 等所有主流 Web 路径
     * Group 1: projectPath, Group 2: branch, Group 3: subPath
     */
    private static final Pattern WEB_GIT_TREE_PATTERN = Pattern.compile("^/(.+?)(?:/-)?/(?:tree|src(?:/(?:branch|tag|commit))?)/([^/]+)(?:/(.*))?$");

    /** 标准 .git 仓库直连克隆链接 */
    private static final Pattern DIRECT_GIT_PATTERN = Pattern.compile("^/(.+?)\\.git$");

    private GitRepoUrlResolver() {
    }

    /**
     * 智能解析 Git 目录树或仓库 URL
     *
     * @param url 输入的 URL 字符串
     * @return 解析后的 GitRepoInfo，若不匹配任何 Git 仓库特征则返回 null
     */
    public static GitRepoInfo resolve(String url) {
        if (StringUtils.isBlank(url)) {
            return null;
        }
        String cleanUrl = StringUtils.trim(url);
        try {
            URI uri = URI.create(cleanUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (StringUtils.isBlank(scheme) || StringUtils.isBlank(host)) {
                return null;
            }

            int port = uri.getPort();
            String serverUrl = scheme + "://" + host + (port > 0 && port != 80 && port != 443 ? ":" + port : "");
            String path = uri.getPath();

            if (StringUtils.isBlank(path)) {
                return null;
            }

            // 1. Web 目录树 URL 通用匹配（/tree/、/-/tree/、/src/branch/、/src/ 等）
            Matcher webTreeMatcher = WEB_GIT_TREE_PATTERN.matcher(path);
            if (webTreeMatcher.matches()) {
                String projectPath = webTreeMatcher.group(1);
                String branch = webTreeMatcher.group(2);
                String subPath = StringUtils.defaultString(webTreeMatcher.group(3), "");
                return buildRepoInfo(serverUrl, projectPath, branch, subPath, cleanUrl);
            }

            // 2. 直连 .git 仓库链接（如 https://github.com/fugary/citsgbt-projects.git）
            Matcher gitMatcher = DIRECT_GIT_PATTERN.matcher(path);
            if (gitMatcher.matches()) {
                String projectPath = gitMatcher.group(1);
                return buildRepoInfo(serverUrl, projectPath, "main", "", cleanUrl);
            }
        } catch (Exception e) {
            log.debug("解析 Git 仓库 URL 异常: url={}", url, e);
        }
        return null;
    }

    private static GitRepoInfo buildRepoInfo(String serverUrl, String projectPath, String branch, String subPath, String rawUrl) {
        String cleanProjectPath = cleanSubPath(projectPath);
        int lastSlash = cleanProjectPath.lastIndexOf('/');
        String owner = lastSlash > 0 ? cleanProjectPath.substring(0, lastSlash) : cleanProjectPath;
        String repo = lastSlash > 0 ? cleanProjectPath.substring(lastSlash + 1) : cleanProjectPath;
        String cloneUrl = serverUrl + "/" + cleanProjectPath + ".git";

        return GitRepoInfo.builder()
                .serverUrl(serverUrl)
                .owner(owner)
                .repo(repo)
                .projectPath(cleanProjectPath)
                .branch(StringUtils.defaultIfBlank(branch, "main"))
                .subPath(cleanSubPath(subPath))
                .cloneUrl(cloneUrl)
                .rawUrl(rawUrl)
                .build();
    }

    private static String cleanSubPath(String subPath) {
        if (StringUtils.isBlank(subPath)) {
            return "";
        }
        String clean = subPath.trim();
        while (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean;
    }
}
