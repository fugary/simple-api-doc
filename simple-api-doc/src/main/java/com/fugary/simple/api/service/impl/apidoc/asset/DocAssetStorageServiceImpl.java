package com.fugary.simple.api.service.impl.apidoc.asset;

import com.fugary.simple.api.service.apidoc.asset.DocAssetStorageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档静态资源（图片等）存储与链接替换实现
 *
 * @author gary.fu
 */
@Slf4j
@Service
public class DocAssetStorageServiceImpl implements DocAssetStorageService {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "svg", "webp", "bmp", "ico"
    );

    private static final Pattern MD_IMAGE_PATTERN = Pattern.compile("(!\\[[^\\]]*\\]\\()([^)\\s]+)(\\s*[\"'][^\"']*[\"'])?\\)");
    private static final Pattern HTML_IMAGE_PATTERN = Pattern.compile("(<img\\b[^>]*?\\bsrc=[\"'])([^\"']+)([\"'][^>]*>)");

    @Value("${dbs.h2.data-dir:~}")
    private String baseDataDir;

    @Override
    public String getBaseUploadPath() {
        return String.join(File.separator, getActualDataDir(), "upload");
    }

    protected String getActualDataDir() {
        if (StringUtils.isBlank(baseDataDir) || "~".equals(baseDataDir)) {
            return System.getProperty("user.home");
        }
        if (baseDataDir.startsWith("~" + File.separator) || baseDataDir.startsWith("~/")) {
            return System.getProperty("user.home") + baseDataDir.substring(1);
        }
        return baseDataDir;
    }

    @Override
    public String saveImage(byte[] imageBytes, String originalFileName, String projectCode) {
        if (imageBytes == null || imageBytes.length == 0) {
            return null;
        }

        String cleanProjectCode = StringUtils.isNotBlank(projectCode)
                ? projectCode.trim().replaceAll("[^a-zA-Z0-9._-]", "_")
                : "default";

        String md5 = DigestUtils.md5Hex(imageBytes);
        String ext = FilenameUtils.getExtension(originalFileName);
        if (StringUtils.isBlank(ext)) {
            ext = "png";
        }
        String fileName = md5 + "." + ext.toLowerCase();

        String relativePath = "docs/" + cleanProjectCode;
        String filePath = String.join(File.separator, getBaseUploadPath(), "docs", cleanProjectCode);
        String targetUrl = "/upload/" + relativePath.replace('\\', '/') + "/" + fileName;

        File targetDir = new File(filePath);
        File targetFile = new File(targetDir, fileName);

        if (targetFile.exists() && targetFile.length() == imageBytes.length) {
            log.debug("图片已存在，直接复用: url={}", targetUrl);
            return targetUrl;
        }

        try {
            FileUtils.forceMkdir(targetDir);
            Files.write(targetFile.toPath(), imageBytes);
            log.info("保存文档图片成功: url={}, size={} bytes", targetUrl, imageBytes.length);
            return targetUrl;
        } catch (IOException e) {
            log.error("保存文档图片失败: targetFile={}", targetFile, e);
            return null;
        }
    }

    @Override
    public String replaceRelativeImages(String markdownContent, String currentDocPath, Map<String, String> imagePathToUrlMap) {
        if (StringUtils.isBlank(markdownContent) || imagePathToUrlMap == null || imagePathToUrlMap.isEmpty()) {
            return markdownContent;
        }

        String docDir = getDocDirectory(currentDocPath);

        // 1. 替换 Markdown 格式图片语法：![alt](path)
        Matcher mdMatcher = MD_IMAGE_PATTERN.matcher(markdownContent);
        StringBuilder mdSb = new StringBuilder();
        while (mdMatcher.find()) {
            String prefix = mdMatcher.group(1);
            String rawImgSrc = mdMatcher.group(2);
            String title = mdMatcher.group(3) != null ? mdMatcher.group(3) : "";

            String newUrl = resolveImageUrl(rawImgSrc, docDir, imagePathToUrlMap);
            if (newUrl != null) {
                mdMatcher.appendReplacement(mdSb, Matcher.quoteReplacement(prefix + newUrl + title + ")"));
            } else {
                mdMatcher.appendReplacement(mdSb, Matcher.quoteReplacement(mdMatcher.group(0)));
            }
        }
        mdMatcher.appendTail(mdSb);
        String result = mdSb.toString();

        // 2. 替换 HTML 格式图片语法：<img src="path" />
        Matcher htmlMatcher = HTML_IMAGE_PATTERN.matcher(result);
        StringBuilder htmlSb = new StringBuilder();
        while (htmlMatcher.find()) {
            String prefix = htmlMatcher.group(1);
            String rawImgSrc = htmlMatcher.group(2);
            String suffix = htmlMatcher.group(3);

            String newUrl = resolveImageUrl(rawImgSrc, docDir, imagePathToUrlMap);
            if (newUrl != null) {
                htmlMatcher.appendReplacement(htmlSb, Matcher.quoteReplacement(prefix + newUrl + suffix));
            } else {
                htmlMatcher.appendReplacement(htmlSb, Matcher.quoteReplacement(htmlMatcher.group(0)));
            }
        }
        htmlMatcher.appendTail(htmlSb);

        return htmlSb.toString();
    }

    @Override
    public boolean isImageFile(String path) {
        if (StringUtils.isBlank(path)) {
            return false;
        }
        String ext = FilenameUtils.getExtension(path);
        return StringUtils.isNotBlank(ext) && IMAGE_EXTENSIONS.contains(ext.toLowerCase());
    }

    /**
     * 计算并解析图片相对路径对应的已转存 URL
     */
    protected String resolveImageUrl(String rawImgSrc, String docDir, Map<String, String> imagePathToUrlMap) {
        if (StringUtils.isBlank(rawImgSrc)) {
            return null;
        }
        String cleanSrc = rawImgSrc.trim();
        // 绝对路径或已转换路径不作处理
        if (cleanSrc.startsWith("http://") || cleanSrc.startsWith("https://")
                || cleanSrc.startsWith("data:") || cleanSrc.startsWith("/upload/")) {
            return null;
        }

        // 去除开头的 ./ 前缀
        String normalizedSrc = cleanSrc.startsWith("./") ? cleanSrc.substring(2) : cleanSrc;

        // 尝试方式 1：基于当前文档目录解析相对路径（如 docs/01-guide + ./logo.png -> docs/01-guide/logo.png）
        String resolvedPath = resolveRelativePath(docDir, normalizedSrc);
        if (imagePathToUrlMap.containsKey(resolvedPath)) {
            return imagePathToUrlMap.get(resolvedPath);
        }

        // 尝试方式 2：直接按 cleanSrc 查找
        if (imagePathToUrlMap.containsKey(normalizedSrc)) {
            return imagePathToUrlMap.get(normalizedSrc);
        }

        // 尝试方式 3：按文件名或后缀模糊匹配
        String fileName = FilenameUtils.getName(cleanSrc);
        for (Map.Entry<String, String> entry : imagePathToUrlMap.entrySet()) {
            if (entry.getKey().endsWith("/" + normalizedSrc) || entry.getKey().equals(normalizedSrc)
                    || FilenameUtils.getName(entry.getKey()).equalsIgnoreCase(fileName)) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * 计算相对路径
     */
    protected String resolveRelativePath(String baseDir, String relativePath) {
        if (StringUtils.isBlank(baseDir)) {
            return relativePath.replace('\\', '/');
        }
        try {
            Path base = Path.of(baseDir.replace('\\', '/'));
            Path resolved = base.resolve(relativePath.replace('\\', '/')).normalize();
            return resolved.toString().replace('\\', '/');
        } catch (Exception e) {
            return (baseDir + "/" + relativePath).replace('\\', '/');
        }
    }

    /**
     * 获取文档所在目录路径
     */
    protected String getDocDirectory(String docPath) {
        if (StringUtils.isBlank(docPath)) {
            return "";
        }
        String cleanPath = docPath.replace('\\', '/');
        int lastSlash = cleanPath.lastIndexOf('/');
        return lastSlash > 0 ? cleanPath.substring(0, lastSlash) : "";
    }
}
