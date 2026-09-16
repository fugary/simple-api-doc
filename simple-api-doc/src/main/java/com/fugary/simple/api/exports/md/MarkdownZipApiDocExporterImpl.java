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
import com.fugary.simple.api.utils.SchemaJsonUtils;
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
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.Schema;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
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

    public static final Pattern MD_ANCHOR_LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\(#([^\\)\\s]+)\\)");
    public static final Pattern HTML_ANCHOR_LINK_PATTERN = Pattern.compile("(<a\\b[^>]*?\\bhref=[\"'])#([^\"'\\s>]+)([\"'])", Pattern.CASE_INSENSITIVE);

    @Autowired
    private ApiProjectService apiProjectService;
    @Autowired
    private ApiProjectInfoDetailService apiProjectInfoDetailService;
    @Autowired
    private ApiDocViewGenerator apiDocViewGenerator;
    @Autowired(required = false)
    private DocAssetStorageService docAssetStorageService;
    @Autowired(required = false)
    private Configuration freemarkerConfig;

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

        Map<Integer, ApiProjectInfo> projectInfoMap = projectInfos.stream()
                .filter(info -> info.getId() != null)
                .collect(Collectors.toMap(ApiProjectInfo::getId, Function.identity(), (a, b) -> a));

        boolean withFrontmatter = exportFilter.getWithFrontmatter() == null || Boolean.TRUE.equals(exportFilter.getWithFrontmatter());

        // 收集所有 Markdown 文件条目与引用的静态资源
        List<ZipDocEntry> docEntries = new ArrayList<>();
        Set<String> usedEntryPaths = new HashSet<>();
        boolean hasRootReadme = false;

        // 初始化全局数据模型收集上下文（ZIP 导出模式下统一收拢至 models/models.md）
        MdViewContext context = new MdViewContext();
        context.setGenerateComponents(false);
        Map<String, Schema<?>> schemasMap = new LinkedHashMap<>();
        context.setSchemasMap(schemasMap);
        context.setDirectSchemaNames(new ArrayList<>());
        usedEntryPaths.add("models/models.md");

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
                ApiProjectInfo apiInfo = projectInfoMap.get(apiDocDetail.getInfoId());
                if (apiInfo == null && !projectInfos.isEmpty()) {
                    apiInfo = projectInfos.get(0);
                }
                ApiProjectInfoDetailVo docInfoDetailVo = apiProjectInfoDetailService.parseInfoDetailVo(apiInfo, apiInfoDetails, List.of(apiDocDetail));
                if (docInfoDetailVo == null) {
                    docInfoDetailVo = new ApiProjectInfoDetailVo();
                }
                if (docInfoDetailVo.getSpecVersion() == null && projectInfoDetailVo != null) {
                    docInfoDetailVo.setSpecVersion(projectInfoDetailVo.getSpecVersion());
                }
                apiDocDetail.setProjectInfoDetail(docInfoDetailVo);

                SpecVersion specVersion = SchemaJsonUtils.resolveSpecVersion(docInfoDetailVo.getSpecVersion());
                context.setApiDocDetail(apiDocDetail);
                SimpleModelUtils.processComponents(apiDocDetail, specVersion, schemasMap);
                bodyContent = apiDocViewGenerator.generate(context);
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
                finalContent = MarkdownHeadingUtils.stripFrontmatter(bodyContent);
            }
            docEntries.add(new ZipDocEntry(entryPath, folderNames.size(), finalContent));
        }

        // 若根目录下不存在 README.md，则生成根目录项目概览 README.md
        if (!hasRootReadme) {
            String rootReadmeContent = generateRootReadme(detailVo, projectInfoDetailVo, exportFilter, withFrontmatter);
            String rootReadmePath = calcUniqueEntryPath("", "README.md", usedEntryPaths);
            docEntries.add(0, new ZipDocEntry(rootReadmePath, 0, rootReadmeContent));
        }

        // 若存在数据模型，重写文档中的模型锚点链接为相对路径，并生成独立的 models/models.md
        if (MapUtils.isNotEmpty(schemasMap)) {
            rewriteModelLinks(docEntries, schemasMap);
            String modelsContent = generateModelsMarkdown(schemasMap, context.getDirectSchemaNames(), withFrontmatter);
            if (StringUtils.isNotBlank(modelsContent)) {
                docEntries.add(new ZipDocEntry("models/models.md", 1, modelsContent));
            }
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

        Set<String> missingAssets = new HashSet<>();
        for (ZipDocEntry docEntry : docEntries) {
            String content = docEntry.getContent();
            if (StringUtils.isBlank(content)) {
                continue;
            }

            Matcher matcher = DocAssetStorageService.MD_LOCAL_IMG_PATTERN.matcher(content);
            StringBuilder sb = new StringBuilder();
            boolean rewritten = false;

            int depth = docEntry.getFolderDepth();
            String relativeAssetPrefix = depth == 0 ? "./assets/" : "../".repeat(depth) + "assets/";

            while (matcher.find()) {
                String matchedImgUrl = matcher.group(1);
                String relativePath = matcher.group(2);
                String imgFileName = matcher.group(3);

                // 尝试从磁盘读取图片物理文件
                String assetEntryKey = "assets/" + imgFileName;
                if (!assetMap.containsKey(assetEntryKey) && !missingAssets.contains(assetEntryKey)) {
                    File imgFile = docAssetStorageService.resolveImageFile(relativePath, imgFileName, currentProjectCode);
                    if (imgFile != null) {
                        try {
                            byte[] imgBytes = FileUtils.readFileToByteArray(imgFile);
                            assetMap.put(assetEntryKey, imgBytes);
                        } catch (IOException e) {
                            log.warn("读取本地图片资源失败: path={}", imgFile.getAbsolutePath(), e);
                        }
                    }
                    if (!assetMap.containsKey(assetEntryKey)) {
                        missingAssets.add(assetEntryKey);
                    }
                }

                // 仅在资源成功打包后重写链接，未找到或越界资源保留原始 URL
                if (assetMap.containsKey(assetEntryKey)) {
                    rewritten = true;
                    String replacement = relativeAssetPrefix + imgFileName;
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                }
            }

            if (rewritten) {
                matcher.appendTail(sb);
                docEntry.setContent(sb.toString());
            }
        }

        return assetMap;
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

    /**
     * 批量重写各文档中的数据模型锚点链接为指向 models/models.md 的相对路径
     */
    private void rewriteModelLinks(List<ZipDocEntry> docEntries, Map<String, Schema<?>> schemasMap) {
        if (MapUtils.isEmpty(schemasMap)) {
            return;
        }
        Set<String> schemaNames = schemasMap.keySet();
        for (ZipDocEntry docEntry : docEntries) {
            String content = docEntry.getContent();
            if (StringUtils.isBlank(content)) {
                continue;
            }
            int depth = docEntry.getFolderDepth();
            String relativeModelsPath = depth == 0 ? "./models/models.md" : "../".repeat(depth) + "models/models.md";
            String rewritten = rewriteContentModelLinks(content, relativeModelsPath, schemaNames);
            docEntry.setContent(rewritten);
        }
    }

    /**
     * 重写单个文档内容中的模型链接
     */
    private String rewriteContentModelLinks(String content, String relativeModelsPath, Set<String> schemaNames) {
        if (StringUtils.isBlank(content) || CollectionUtils.isEmpty(schemaNames)) {
            return content;
        }
        // 1. 替换 Markdown 格式链接：[Text](#Anchor) -> [Text](relativeModelsPath#Anchor)
        Matcher mdMatcher = MD_ANCHOR_LINK_PATTERN.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (mdMatcher.find()) {
            String text = mdMatcher.group(1);
            String anchor = mdMatcher.group(2);
            if (isSchemaAnchor(anchor, schemaNames)) {
                mdMatcher.appendReplacement(sb, Matcher.quoteReplacement("[" + text + "](" + relativeModelsPath + "#" + anchor + ")"));
            }
        }
        mdMatcher.appendTail(sb);
        String intermediate = sb.toString();

        // 2. 替换 HTML 格式链接：<a ... href="#Anchor" ...> -> <a ... href="relativeModelsPath#Anchor" ...>
        Matcher htmlMatcher = HTML_ANCHOR_LINK_PATTERN.matcher(intermediate);
        sb = new StringBuilder();
        while (htmlMatcher.find()) {
            String prefix = htmlMatcher.group(1);
            String anchor = htmlMatcher.group(2);
            String quote = htmlMatcher.group(3);
            if (isSchemaAnchor(anchor, schemaNames)) {
                htmlMatcher.appendReplacement(sb, Matcher.quoteReplacement(prefix + relativeModelsPath + "#" + anchor + quote));
            }
        }
        htmlMatcher.appendTail(sb);
        return sb.toString();
    }

    private boolean isSchemaAnchor(String anchor, Set<String> schemaNames) {
        if (StringUtils.isBlank(anchor)) {
            return false;
        }
        if (schemaNames.contains(anchor)) {
            return true;
        }
        try {
            String decoded = URLDecoder.decode(anchor, StandardCharsets.UTF_8);
            return schemaNames.contains(decoded);
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * 生成统一的数据模型文档 models/models.md 内容
     */
    private String generateModelsMarkdown(Map<String, Schema<?>> schemasMap, List<String> directSchemaNames, boolean withFrontmatter) {
        Map<String, Schema<?>> sortedSchemasMap = apiDocViewGenerator.sortSchemasMap(schemasMap, directSchemaNames);
        Map<String, Object> model = new HashMap<>();
        model.put("schemasMap", sortedSchemasMap);
        model.put("withFrontmatter", withFrontmatter);
        try {
            Template template = getFreemarkerConfig().getTemplate("ExportModelsMdView.md.ftl");
            return FreeMarkerTemplateUtils.processTemplateIntoString(template, model);
        } catch (IOException | TemplateException e) {
            log.error("渲染 ExportModelsMdView.md.ftl 失败", e);
            throw new RuntimeException("渲染数据模型文档失败", e);
        }
    }

    /**
     * 获取或初始化 FreeMarker 配置
     */
    public Configuration getFreemarkerConfig() {
        if (freemarkerConfig != null) {
            return freemarkerConfig;
        }
        if (apiDocViewGenerator instanceof MarkdownApiDocViewGeneratorImpl) {
            Configuration cfg = ((MarkdownApiDocViewGeneratorImpl) apiDocViewGenerator).getFreemarkerConfig();
            if (cfg != null) {
                this.freemarkerConfig = cfg;
                return cfg;
            }
        }
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_31);
        cfg.setClassForTemplateLoading(this.getClass(), "/templates");
        cfg.setDefaultEncoding(StandardCharsets.UTF_8.name());
        ApiDocFreemarkerUtils utils = new ApiDocFreemarkerUtils();
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setUseCodeAsDefaultMessage(true);
        utils.setMessageSource(messages);
        try {
            cfg.setSharedVariable("utils", utils);
            cfg.setSharedVariable("message", (TemplateMethodModelEx) arguments -> {
                if (CollectionUtils.isNotEmpty(arguments)) {
                    return messages.getMessage(arguments.get(0).toString(), null, Locale.getDefault());
                }
                return "";
            });
        } catch (TemplateModelException e) {
            log.error("初始化 FreeMarker 默认配置失败", e);
        }
        this.freemarkerConfig = cfg;
        return freemarkerConfig;
    }

    @Data
    @AllArgsConstructor
    private static class ZipDocEntry {
        private String path;
        private int folderDepth;
        private String content;
    }
}
