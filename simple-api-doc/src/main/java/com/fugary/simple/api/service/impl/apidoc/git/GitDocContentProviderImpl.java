package com.fugary.simple.api.service.impl.apidoc.git;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.service.apidoc.asset.DocAssetStorageService;
import com.fugary.simple.api.service.apidoc.git.GitDocContentProvider;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.git.GitRepoInfo;
import com.fugary.simple.api.web.vo.imports.BasicAuthVo;
import com.fugary.simple.api.web.vo.imports.DocSourceData;
import com.fugary.simple.api.web.vo.imports.UrlWithAuthVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 基于 JGit 标准 Git 协议的通用 Git 仓库与文档抓取实现（支持全网任意 Git 平台与多级深层子目录）
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
        if (repoInfo == null || StringUtils.isBlank(repoInfo.getCloneUrl())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2009);
        }

        String cloneUrl = repoInfo.getCloneUrl();
        String branch = StringUtils.defaultIfBlank(repoInfo.getBranch(), "main");
        String subPath = repoInfo.getSubPath();
        String projectCode = StringUtils.defaultIfBlank(repoInfo.getRepo(), "default");

        File tempDir = null;
        try {
            tempDir = Files.createTempDirectory("simple_api_doc_git_").toFile();
            log.info("开始 JGit 浅拉取仓库: cloneUrl={}, branch={}, subPath={}, tempDir={}", cloneUrl, branch, subPath, tempDir.getAbsolutePath());

            CredentialsProvider credentialsProvider = resolveCredentialsProvider(source);
            doCloneRepository(cloneUrl, branch, tempDir, credentialsProvider);

            // 定位目标子目录
            File targetDir = StringUtils.isNotBlank(subPath) ? new File(tempDir, subPath) : tempDir;
            if (!targetDir.exists() || !targetDir.isDirectory()) {
                log.warn("Git 目标子目录不存在: subPath={}, targetDir={}", subPath, targetDir.getAbsolutePath());
                return SimpleResultUtils.createError(SystemErrorConstants.CODE_2009, "Git 仓库中未找到目标子目录: " + subPath);
            }

            // 1. 扫描同目录/子目录下的所有文件
            List<Path> allFiles;
            try (Stream<Path> stream = Files.walk(targetDir.toPath())) {
                allFiles = stream.filter(Files::isRegularFile).collect(Collectors.toList());
            }

            // 2. 转存静态图片并构建映射表
            Map<String, String> imagePathToUrlMap = new HashMap<>();
            List<Path> markdownPaths = new ArrayList<>();

            for (Path filePath : allFiles) {
                String fileName = filePath.getFileName().toString();
                String relativeToTarget = targetDir.toPath().relativize(filePath).toString().replace('\\', '/');
                String relativeToRoot = tempDir.toPath().relativize(filePath).toString().replace('\\', '/');

                if (isMarkdownFile(fileName)) {
                    markdownPaths.add(filePath);
                } else if (docAssetStorageService != null && docAssetStorageService.isImageFile(fileName)) {
                    try {
                        byte[] imageBytes = Files.readAllBytes(filePath);
                        String localUrl = docAssetStorageService.saveImage(imageBytes, relativeToTarget, projectCode);
                        if (StringUtils.isNotBlank(localUrl)) {
                            imagePathToUrlMap.put(relativeToTarget, localUrl);
                            imagePathToUrlMap.put(relativeToRoot, localUrl);
                            imagePathToUrlMap.put(fileName, localUrl);
                        }
                    } catch (Exception e) {
                        log.warn("JGit 读取图片文件异常: path={}", filePath, e);
                    }
                }
            }

            // 3. 读取 Markdown 正文并替换相对图片链接
            List<Map<String, String>> docFiles = new ArrayList<>();
            for (Path mdPath : markdownPaths) {
                String relativeToTarget = targetDir.toPath().relativize(mdPath).toString().replace('\\', '/');
                try {
                    String content = Files.readString(mdPath, StandardCharsets.UTF_8);
                    if (docAssetStorageService != null && !imagePathToUrlMap.isEmpty()) {
                        content = docAssetStorageService.replaceRelativeImages(content, relativeToTarget, imagePathToUrlMap);
                    }
                    Map<String, String> fileMap = new LinkedHashMap<>();
                    fileMap.put("path", relativeToTarget);
                    fileMap.put("content", content);
                    docFiles.add(fileMap);
                } catch (Exception e) {
                    log.warn("JGit 读取 Markdown 文件异常: path={}", mdPath, e);
                }
            }

            if (docFiles.isEmpty()) {
                return SimpleResultUtils.createError(SystemErrorConstants.CODE_2009, "Git 目标目录下未找到 Markdown 文档 (*.md): " + (StringUtils.isNotBlank(subPath) ? subPath : "/"));
            }

            log.info("JGit 成功拉取 Markdown 文档: count={}, subPath={}", docFiles.size(), subPath);
            return SimpleResultUtils.createSimpleResult(DocSourceData.ofText(JsonUtils.toJson(docFiles)));

        } catch (TransportException e) {
            log.error("JGit 传输异常: cloneUrl={}", cloneUrl, e);
            String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (message.contains("not authorized") || message.contains("authentication not supported") || message.contains("401") || message.contains("403")) {
                boolean hasAuth = source != null && !"none".equalsIgnoreCase(source.getAuthType()) && StringUtils.isNotBlank(source.getAuthContent());
                if (!hasAuth) {
                    return SimpleResultUtils.createError(SystemErrorConstants.CODE_2009, "访问 Git 仓库认证失败，若为私有仓库请在【认证方式】中选择【Token认证】或【Basic认证】并填入访问凭据");
                }
                return SimpleResultUtils.createError(SystemErrorConstants.CODE_2009, "访问 Git 仓库认证失败，请检查 Token / 密码权限是否正确");
            }
            if (message.contains("not found") || message.contains("404")) {
                return SimpleResultUtils.createError(SystemErrorConstants.CODE_2009, "访问 Git 仓库失败(404 Not Found)，请检查仓库地址或分支是否正确: " + cloneUrl);
            }
            return SimpleResultUtils.createError(SystemErrorConstants.CODE_2009, "Git 仓库拉取失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("JGit 克隆与解析异常: cloneUrl={}", cloneUrl, e);
            String baseMsg = SimpleResultUtils.getErrorMsg(SystemErrorConstants.CODE_2009);
            return SimpleResultUtils.createError(SystemErrorConstants.CODE_2009, baseMsg + ": " + e.getMessage());
        } finally {
            // 清理临时目录，保证零磁盘垃圾残留
            if (tempDir != null && tempDir.exists()) {
                try {
                    FileUtils.deleteDirectory(tempDir);
                } catch (Exception e) {
                    log.warn("清理 JGit 临时目录异常: dir={}", tempDir.getAbsolutePath(), e);
                }
            }
        }
    }

    /**
     * 执行 JGit 快速克隆（带分支 Fallback 容错）
     */
    protected void doCloneRepository(String cloneUrl, String branch, File tempDir, CredentialsProvider credentialsProvider) throws Exception {
        String branchRef = branch.startsWith("refs/") ? branch : "refs/heads/" + branch;
        CloneCommand cloneCmd = Git.cloneRepository()
                .setURI(cloneUrl)
                .setDirectory(tempDir)
                .setBranch(branchRef)
                .setCloneAllBranches(false)
                .setBranchesToClone(Collections.singletonList(branchRef));

        if (credentialsProvider != null) {
            cloneCmd.setCredentialsProvider(credentialsProvider);
        }

        try (Git git = cloneCmd.call()) {
            // 单分支克隆成功
        } catch (GitAPIException e) {
            // 若指定分支 (如 main / master) 失败，尝试不指定分支浅克隆默认 HEAD 分支
            if ("main".equalsIgnoreCase(branch) || "master".equalsIgnoreCase(branch)) {
                log.info("指定分支克隆失败，尝试克隆默认分支: cloneUrl={}", cloneUrl);
                FileUtils.cleanDirectory(tempDir);

                CloneCommand defaultBranchCmd = Git.cloneRepository()
                        .setURI(cloneUrl)
                        .setDirectory(tempDir)
                        .setCloneAllBranches(false);

                if (credentialsProvider != null) {
                    defaultBranchCmd.setCredentialsProvider(credentialsProvider);
                }

                try (Git git = defaultBranchCmd.call()) {
                    // 默认分支克隆成功
                    return;
                }
            }
            throw e;
        }
    }

    /**
     * 解析 Git 认证凭据 Provider
     */
    protected CredentialsProvider resolveCredentialsProvider(UrlWithAuthVo source) {
        if (source == null || StringUtils.isBlank(source.getAuthType())) {
            return null;
        }
        String authType = StringUtils.lowerCase(source.getAuthType());
        if (StringUtils.equalsIgnoreCase(authType, ApiDocConstants.AUTH_TYPE_BASIC)) {
            BasicAuthVo basicAuth = JsonUtils.fromJson(source.getAuthContent(), BasicAuthVo.class);
            if (basicAuth != null) {
                return new UsernamePasswordCredentialsProvider(
                        StringUtils.defaultString(basicAuth.getUserName()),
                        StringUtils.defaultString(basicAuth.getUserPassword())
                );
            }
        } else if ("token".equalsIgnoreCase(authType) && StringUtils.isNotBlank(source.getAuthContent())) {
            String authContent = source.getAuthContent().trim();
            if (authContent.startsWith("{")) {
                try {
                    Map<String, Object> authMap = JsonUtils.fromJson(authContent, new TypeReference<>() {});
                    if (authMap != null) {
                        String token = Objects.toString(authMap.get("token"), "");
                        String username = Objects.toString(authMap.get("username"), "oauth2");
                        if (StringUtils.isNotBlank(token)) {
                            return new UsernamePasswordCredentialsProvider(username, token.trim());
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析 Token 配置异常", e);
                }
            } else {
                // 纯文本 Token：通用 Git Smart HTTP 使用 oauth2 / token
                return new UsernamePasswordCredentialsProvider("oauth2", authContent);
            }
        }
        return null;
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
}
