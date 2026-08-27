package com.fugary.simple.api.utils.git;

import com.fugary.simple.api.web.vo.git.GitRepoInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Git 仓库与目录树 URL 智能解析器
 *
 * @author gary.fu
 */
@Slf4j
public class GitRepoUrlResolver {

    private static final Pattern GITHUB_TREE_PATTERN = Pattern.compile("^/([^/]+)/([^/]+)/tree/([^/]+)(?:/(.*))?$");
    private static final Pattern GITEE_TREE_PATTERN = Pattern.compile("^/([^/]+)/([^/]+)/tree/([^/]+)(?:/(.*))?$");
    private static final Pattern GITLAB_TREE_PATTERN = Pattern.compile("^/(.+?)(?:/-)?/tree/([^/]+)(?:/(.*))?$");

    private GitRepoUrlResolver() {
    }

    /**
     * 智能解析 Git 目录树 URL
     *
     * @param url 输入的 URL 字符串
     * @return 解析后的 GitRepoInfo，若不匹配任何 Git 目录树结构则返回 null
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

            if (StringUtils.isBlank(path) || !path.contains("/tree/")) {
                return null;
            }

            // 1. 优先判定 GitHub (github.com)
            if ("github.com".equalsIgnoreCase(host) || host.endsWith(".github.com")) {
                Matcher matcher = GITHUB_TREE_PATTERN.matcher(path);
                if (matcher.matches()) {
                    String owner = matcher.group(1);
                    String repo = matcher.group(2);
                    String branch = matcher.group(3);
                    String subPath = StringUtils.defaultString(matcher.group(4), "");
                    return GitRepoInfo.builder()
                            .platform(GitRepoInfo.Platform.GITHUB)
                            .serverUrl(serverUrl)
                            .owner(owner)
                            .repo(repo)
                            .projectPath(owner + "/" + repo)
                            .branch(branch)
                            .subPath(cleanSubPath(subPath))
                            .rawUrl(cleanUrl)
                            .build();
                }
            }

            // 2. 判定 Gitee (gitee.com)
            if ("gitee.com".equalsIgnoreCase(host) || host.endsWith(".gitee.com")) {
                Matcher matcher = GITEE_TREE_PATTERN.matcher(path);
                if (matcher.matches()) {
                    String owner = matcher.group(1);
                    String repo = matcher.group(2);
                    String branch = matcher.group(3);
                    String subPath = StringUtils.defaultString(matcher.group(4), "");
                    return GitRepoInfo.builder()
                            .platform(GitRepoInfo.Platform.GITEE)
                            .serverUrl(serverUrl)
                            .owner(owner)
                            .repo(repo)
                            .projectPath(owner + "/" + repo)
                            .branch(branch)
                            .subPath(cleanSubPath(subPath))
                            .rawUrl(cleanUrl)
                            .build();
                }
            }

            // 3. 判定 GitLab (包含 /-/tree/ 特征，或私有部署 host 包含 gitlab)
            if (path.contains("/-/tree/") || host.contains("gitlab") || path.contains("/tree/")) {
                Matcher matcher = GITLAB_TREE_PATTERN.matcher(path);
                if (matcher.matches()) {
                    String projectPath = matcher.group(1);
                    String branch = matcher.group(2);
                    String subPath = StringUtils.defaultString(matcher.group(3), "");
                    int lastSlash = projectPath.lastIndexOf('/');
                    String owner = lastSlash > 0 ? projectPath.substring(0, lastSlash) : projectPath;
                    String repo = lastSlash > 0 ? projectPath.substring(lastSlash + 1) : projectPath;
                    return GitRepoInfo.builder()
                            .platform(GitRepoInfo.Platform.GITLAB)
                            .serverUrl(serverUrl)
                            .owner(owner)
                            .repo(repo)
                            .projectPath(projectPath)
                            .branch(branch)
                            .subPath(cleanSubPath(subPath))
                            .rawUrl(cleanUrl)
                            .build();
                }
            }
        } catch (Exception e) {
            log.debug("解析 Git 仓库 URL 异常: url={}", url, e);
        }
        return null;
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
