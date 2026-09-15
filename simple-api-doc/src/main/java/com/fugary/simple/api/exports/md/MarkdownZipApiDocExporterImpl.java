package com.fugary.simple.api.exports.md;

import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiFolder;
import com.fugary.simple.api.entity.api.ApiProjectInfo;
import com.fugary.simple.api.entity.api.ApiProjectInfoDetail;
import com.fugary.simple.api.exception.SimpleRuntimeException;
import com.fugary.simple.api.exports.ApiDocExporter;
import com.fugary.simple.api.exports.ApiDocViewGenerator;
import com.fugary.simple.api.exports.ApiExportFilter;
import com.fugary.simple.api.imports.markdown.MarkdownDocImporterImpl;
import com.fugary.simple.api.service.apidoc.ApiProjectInfoDetailService;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.service.apidoc.asset.DocAssetStorageService;
import com.fugary.simple.api.utils.SchemaYamlUtils;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.exports.ApiDocParseUtils;
import com.fugary.simple.api.web.vo.exports.ExportEnvConfigVo;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectInfoDetailVo;
import com.fugary.simple.api.web.vo.query.ProjectDetailQueryVo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 按当前文件夹树结构导出 Markdown 多级目录 ZIP 压缩包实现
 *
 * @author gary.fu
 */
@Slf4j
@Setter
@Getter
@Component
public class MarkdownZipApiDocExporterImpl implements ApiDocExporter<byte[]> {

    private static final Pattern MD_LOCAL_IMG_PATTERN = Pattern.compile("(/upload/((?:[^/\\s)\"'>]+/)*([a-zA-Z0-9._-]+\\.[a-zA-Z0-9]+)))");

    @Autowired
    private ApiProjectService apiProjectService;
    @Autowired
    private ApiProjectInfoDetailService apiProjectInfoDetailService;
    @Autowired
    private ApiDocViewGenerator apiDocViewGenerator;
    @Autowired(required = false)
    private DocAssetStorageService docAssetStorageService;

    @Override
    public byte[] export(Integer projectId, ApiExportFilter exportFilter) {
        List<Integer> docIds = exportFilter.getDocIds();
        ProjectDetailQueryVo queryVo = ProjectDetailQueryVo.builder()
                .projectId(projectId)
                .includeDocs(true)
                .forceEnabled(true)
                .build();
        ApiProjectDetailVo detailVo = apiProjectService.loadProjectVo(queryVo);
        // 解析文件夹，方便后续读取
        Map<Integer, ApiFolder> folderMap = detailVo.getFolders().stream()
                .collect(Collectors.toMap(ApiFolder::getId, Function.identity()));
        List<ApiDoc> docList = detailVo.getDocs();
        if (CollectionUtils.isNotEmpty(docIds)) { // 过滤指定文档
            docList = docList.stream().filter(apiDoc -> docIds.contains(apiDoc.getId()))
                    .collect(Collectors.toList());
        }
        // 过滤被禁用文件夹的数据
        docList = docList.stream().filter(doc -> folderMap.get(doc.getFolderId()) != null)
                .collect(Collectors.toList());
        if (docList.isEmpty()) {
            throw new SimpleRuntimeException(SystemErrorConstants.CODE_2011);
        }
        Set<Integer> infoIds = docList.stream().map(ApiDoc::getInfoId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (infoIds.size() > 1) {
            throw new SimpleRuntimeException(SystemErrorConstants.CODE_2010);
        }

        // 加载文档详情
        List<ApiDocDetailVo> docDetailList = new ArrayList<>(apiProjectInfoDetailService.loadDetailList(docList));
        // 加载项目schema和security数据
        List<ApiProjectInfoDetail> apiInfoDetails = apiProjectInfoDetailService.loadByProject(projectId, ApiDocConstants.PROJECT_SCHEMA_TYPES);
        List<ApiProjectInfo> projectInfos = SimpleModelUtils.filterApiProjectInfo(detailVo, infoIds);
        List<ApiProjectInfoDetailVo> projectInfoDetails = projectInfos.stream()
                .map(projectInfo -> apiProjectInfoDetailService.parseInfoDetailVo(projectInfo, apiInfoDetails, docDetailList))
                .collect(Collectors.toList());
        // 提取和文档相关的schema和security数据
        ApiProjectInfoDetailVo projectInfoDetailVo = apiProjectInfoDetailService.mergeInfoDetailVo(projectInfoDetails);
        if (projectInfoDetailVo == null) {
            projectInfoDetailVo = new ApiProjectInfoDetailVo();
        }
        // 对 docDetailList 按照树形结构排序（保证输出顺序与 UI 树一致）
        docDetailList.sort(Comparator.comparing(d -> ApiDocParseUtils.getDocSortKey(d, folderMap)));

        boolean withFrontmatter = exportFilter.getWithFrontmatter() == null || Boolean.TRUE.equals(exportFilter.getWithFrontmatter());

        // 收集所有 Markdown 文件条目与引用的静态资源
        List<ZipDocEntry> docEntries = new ArrayList<>();
        Set<String> usedEntryPaths = new HashSet<>();
        boolean hasRootReadme = false;

        for (ApiDocDetailVo apiDocDetail : docDetailList) {
            List<String> folderNames = getSanitizedFolderNames(apiDocDetail.getFolderId(), folderMap);
            String folderPath = String.join("/", folderNames);
            String docFileName = getDocFileName(apiDocDetail);
            String entryPath = calcUniqueEntryPath(folderPath, docFileName, usedEntryPaths);

            if (StringUtils.isBlank(folderPath) && isReadmeOrIndex(docFileName)) {
                hasRootReadme = true;
            }

            String bodyContent;
            if (ApiDocConstants.DOC_TYPE_API.equals(apiDocDetail.getDocType())) {
                apiDocDetail.setProject(detailVo);
                apiDocDetail.setProjectInfoDetail(projectInfoDetailVo);
                bodyContent = apiDocViewGenerator.generate(new MdViewContext(apiDocDetail));
                String docTitle = StringUtils.defaultIfBlank(apiDocDetail.getDocName(), apiDocDetail.getUrl());
                if (StringUtils.isNotBlank(docTitle) && !bodyContent.startsWith("# ")) {
                    bodyContent = "# " + docTitle + "\n\n" + bodyContent;
                }
            } else {
                bodyContent = StringUtils.defaultString(apiDocDetail.getDocContent());
            }

            String finalContent;
            if (withFrontmatter) {
                finalContent = buildMarkdownContentWithFrontmatter(apiDocDetail, bodyContent);
            } else {
                finalContent = stripFrontmatterIfPresent(bodyContent);
            }
            docEntries.add(new ZipDocEntry(entryPath, folderNames.size(), finalContent));
        }

        // 若根目录下不存在 README.md，则生成根目录项目概览 README.md
        if (!hasRootReadme) {
            String rootReadmeContent = generateRootReadme(detailVo, projectInfoDetailVo, exportFilter, withFrontmatter);
            String rootReadmePath = calcUniqueEntryPath("", "README.md", usedEntryPaths);
            docEntries.add(0, new ZipDocEntry(rootReadmePath, 0, rootReadmeContent));
        }

        // 提取项目引用的静态图片资源并打包到 assets/ 目录，同时将文档内图片链接重写为自适应相对路径
        Map<String, byte[]> assetMap = bundleAssetsAndRewriteImages(docEntries, detailVo.getProjectCode());

        // 打包为 ZIP 字节流
        return packageToZipBytes(docEntries, assetMap);
    }

    /**
     * 打包为 ZIP 字节流
     */
    private byte[] packageToZipBytes(List<ZipDocEntry> docEntries, Map<String, byte[]> assetMap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            // 写入图片等静态资源
            if (assetMap != null && !assetMap.isEmpty()) {
                for (Map.Entry<String, byte[]> entry : assetMap.entrySet()) {
                    zos.putNextEntry(new ZipEntry(entry.getKey()));
                    zos.write(entry.getValue());
                    zos.closeEntry();
                }
            }

            // 写入各个 Markdown 文档
            for (ZipDocEntry docEntry : docEntries) {
                zos.putNextEntry(new ZipEntry(docEntry.getPath()));
                zos.write(docEntry.getContent().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        } catch (IOException e) {
            log.error("生成 Markdown ZIP 压缩包失败", e);
            throw new RuntimeException("生成 Markdown ZIP 压缩包失败", e);
        }
        return baos.toByteArray();
    }

    /**
     * 提取图片资源并重写文档中图片相对链接
     */
    private Map<String, byte[]> bundleAssetsAndRewriteImages(List<ZipDocEntry> docEntries, String currentProjectCode) {
        Map<String, byte[]> assetMap = new LinkedHashMap<>();
        if (docAssetStorageService == null) {
            return assetMap;
        }

        String baseUploadPath = docAssetStorageService.getBaseUploadPath();
        for (ZipDocEntry docEntry : docEntries) {
            String content = docEntry.getContent();
            if (StringUtils.isBlank(content)) {
                continue;
            }

            Matcher matcher = MD_LOCAL_IMG_PATTERN.matcher(content);
            StringBuilder sb = new StringBuilder();
            boolean found = false;

            int depth = docEntry.getFolderDepth();
            String relativeAssetPrefix = depth == 0 ? "./assets/" : "../".repeat(depth) + "assets/";

            while (matcher.find()) {
                found = true;
                String matchedImgUrl = matcher.group(1);
                String relativePath = matcher.group(2);
                String imgFileName = matcher.group(3);

                // 尝试从磁盘读取图片物理文件
                String assetEntryKey = "assets/" + imgFileName;
                if (!assetMap.containsKey(assetEntryKey)) {
                    File imgFile = resolveImageFile(baseUploadPath, relativePath, imgFileName, currentProjectCode);
                    if (imgFile != null) {
                        try {
                            byte[] imgBytes = FileUtils.readFileToByteArray(imgFile);
                            assetMap.put(assetEntryKey, imgBytes);
                        } catch (IOException e) {
                            log.warn("读取本地图片资源失败: path={}", imgFile.getAbsolutePath(), e);
                        }
                    }
                }

                // 将 Markdown 中的绝对 URL 替换为相对路径
                String replacement = relativeAssetPrefix + imgFileName;
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }

            if (found) {
                matcher.appendTail(sb);
                docEntry.setContent(sb.toString());
            }
        }

        return assetMap;
    }

    /**
     * 阶梯式自适应定位物理图片文件（支持根目录上传、项目隔离目录以及跨项目引用图片）
     *
     * @param baseUploadPath      基础上传目录
     * @param relativePath        /upload/ 后的相对路径（如 docs/citsgbt/abc.png 或 9b1e19...jpg）
     * @param imgFileName         图片文件名
     * @param currentProjectCode  当前项目 Code
     * @return 存在的图片文件，若不存在返回 null
     */
    protected File resolveImageFile(String baseUploadPath, String relativePath, String imgFileName, String currentProjectCode) {
        if (StringUtils.isBlank(baseUploadPath) || StringUtils.isBlank(imgFileName)) {
            return null;
        }
        // 1. 优先按完整相对路径查找（支持 /upload/docs/{projectCode}/{fileName}、/upload/{fileName} 或跨项目路径）
        if (StringUtils.isNotBlank(relativePath)) {
            File imgFile = new File(baseUploadPath, relativePath.replace('/', File.separatorChar));
            if (isValidImageFile(imgFile, baseUploadPath)) {
                return imgFile;
            }
        }
        // 2. 尝试在当前项目 docs/{currentProjectCode}/ 下查找
        if (StringUtils.isNotBlank(currentProjectCode)) {
            File imgFile = new File(String.join(File.separator, baseUploadPath, "docs", currentProjectCode, imgFileName));
            if (isValidImageFile(imgFile, baseUploadPath)) {
                return imgFile;
            }
        }
        // 3. 尝试在 upload 根目录下查找
        File rootImgFile = new File(baseUploadPath, imgFileName);
        if (isValidImageFile(rootImgFile, baseUploadPath)) {
            return rootImgFile;
        }
        return null;
    }

    /**
     * 校验文件是否存在且防止路径遍历攻击
     */
    private boolean isValidImageFile(File file, String baseUploadPath) {
        if (file == null || !file.exists() || !file.isFile()) {
            return false;
        }
        try {
            return file.getCanonicalPath().startsWith(new File(baseUploadPath).getCanonicalPath());
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 生成根目录 README.md 内容
     */
    private String generateRootReadme(ApiProjectDetailVo project, ApiProjectInfoDetailVo projectInfoDetail, ApiExportFilter exportFilter, boolean withFrontmatter) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(project.getProjectName()).append("\n\n");

        String version = StringUtils.defaultIfBlank(project.getApiVersion(), projectInfoDetail != null ? projectInfoDetail.getVersion() : null);
        if (StringUtils.isNotBlank(version)) {
            sb.append("**Version**: ").append(version).append("\n\n");
        }

        if (StringUtils.isNotBlank(project.getDescription())) {
            sb.append(project.getDescription()).append("\n\n");
        }

        if (StringUtils.isNotBlank(project.getEnvContent())) {
            List<ExportEnvConfigVo> envList = ApiDocParseUtils.getFilteredEnvConfigs(project.getEnvContent(), exportFilter.getEnvContent());
            if (CollectionUtils.isNotEmpty(envList)) {
                sb.append("## Environments\n\n");
                for (ExportEnvConfigVo env : envList) {
                    sb.append("- **").append(env.getName()).append("**: `").append(env.getUrl()).append("`\n");
                }
                sb.append("\n");
            }
        }

        if (withFrontmatter) {
            Map<String, Object> frontmatter = new LinkedHashMap<>();
            frontmatter.put("title", project.getProjectName());
            frontmatter.put("order", 1);
            String yaml = SchemaYamlUtils.toYaml(frontmatter, false);
            return "---\n" + yaml.trim() + "\n---\n\n" + sb.toString();
        }
        return sb.toString();
    }

    /**
     * 包装 Markdown 文档内容并注入/更新 Frontmatter 元数据
     */
    private String buildMarkdownContentWithFrontmatter(ApiDocDetailVo docDetail, String bodyContent) {
        String rawBody = bodyContent != null ? bodyContent : "";
        Map<String, Object> frontmatter = new LinkedHashMap<>();

        // 1. 若原始正文包含 Frontmatter，解析已有字段
        Matcher fmM = MarkdownDocImporterImpl.FRONTMATTER_PATTERN.matcher(rawBody);
        if (fmM.matches()) {
            String yamlStr = fmM.group(1);
            rawBody = fmM.group(2);
            try {
                Map<String, Object> existingFm = SchemaYamlUtils.fromYaml(yamlStr, Map.class, false);
                if (existingFm != null) {
                    frontmatter.putAll(existingFm);
                }
            } catch (Exception e) {
                log.debug("解析已有 Frontmatter 异常", e);
            }
        }

        // 2. 使用最新实体属性覆盖 Frontmatter 核心字段
        if (StringUtils.isNotBlank(docDetail.getDocName())) {
            frontmatter.put("title", docDetail.getDocName());
        }
        if (docDetail.getSortId() != null) {
            frontmatter.put("order", docDetail.getSortId());
        }
        if (StringUtils.isNotBlank(docDetail.getDescription())) {
            frontmatter.put("description", docDetail.getDescription());
        }
        if (Boolean.TRUE.equals(docDetail.getDeprecated())) {
            frontmatter.put("deprecated", true);
        } else {
            frontmatter.remove("deprecated");
        }
        if (Boolean.TRUE.equals(docDetail.getLocked())) {
            frontmatter.put("locked", true);
        } else {
            frontmatter.remove("locked");
        }
        if (ApiDocConstants.DOC_TYPE_API.equals(docDetail.getDocType())) {
            frontmatter.put("docType", "api");
            if (StringUtils.isNotBlank(docDetail.getMethod())) {
                frontmatter.put("method", docDetail.getMethod());
            }
            if (StringUtils.isNotBlank(docDetail.getUrl())) {
                frontmatter.put("url", docDetail.getUrl());
            }
        }

        if (frontmatter.isEmpty()) {
            return rawBody;
        }

        String yaml = SchemaYamlUtils.toYaml(frontmatter, false);
        if (StringUtils.isBlank(yaml)) {
            return rawBody;
        }
        return "---\n" + yaml.trim() + "\n---\n\n" + rawBody.stripLeading();
    }

    /**
     * 剥离正文可能包含的 Frontmatter 头部
     */
    private String stripFrontmatterIfPresent(String content) {
        if (StringUtils.isBlank(content)) {
            return "";
        }
        Matcher matcher = MarkdownDocImporterImpl.FRONTMATTER_PATTERN.matcher(content);
        if (matcher.matches()) {
            return matcher.group(2).stripLeading();
        }
        return content;
    }

    /**
     * 计算文档文件名
     */
    private String getDocFileName(ApiDocDetailVo docDetail) {
        String name = docDetail.getDocName();
        if (StringUtils.isBlank(name) && StringUtils.isNotBlank(docDetail.getDocKey())) {
            name = docDetail.getDocKey();
        }
        String sanitized = sanitizePathSegment(name);
        if (!sanitized.toLowerCase().endsWith(".md") && !sanitized.toLowerCase().endsWith(".markdown")) {
            sanitized = sanitized + ".md";
        }
        return sanitized;
    }

    /**
     * 计算 ZIP 内部唯一路径
     */
    private String calcUniqueEntryPath(String folderPath, String fileName, Set<String> usedPaths) {
        String baseName = FilenameUtils.getBaseName(fileName);
        String extension = FilenameUtils.getExtension(fileName);
        if (StringUtils.isBlank(extension)) {
            extension = "md";
        }
        String prefix = StringUtils.isNotBlank(folderPath) ? folderPath + "/" : "";
        String candidate = prefix + fileName;
        int index = 1;
        while (usedPaths.contains(candidate.toLowerCase())) {
            candidate = prefix + baseName + "_" + index + "." + extension;
            index++;
        }
        usedPaths.add(candidate.toLowerCase());
        return candidate;
    }

    /**
     * 过滤文件名及路径非法字符（防止路径穿越与非法命名）
     */
    private String sanitizePathSegment(String name) {
        if (StringUtils.isBlank(name)) {
            return "unnamed";
        }
        String clean = name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        return StringUtils.defaultIfBlank(clean, "unnamed");
    }

    private boolean isReadmeOrIndex(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return false;
        }
        String base = FilenameUtils.getBaseName(fileName).toLowerCase();
        return "readme".equals(base) || "index".equals(base);
    }

    private List<String> getSanitizedFolderNames(Integer folderId, Map<Integer, ApiFolder> folderMap) {
        return ApiDocParseUtils.getFolderNames(folderId, folderMap).stream()
                .map(this::sanitizePathSegment)
                .collect(Collectors.toList());
    }

    @Data
    @AllArgsConstructor
    private static class ZipDocEntry {
        private String path;
        private int folderDepth;
        private String content;
    }
}
