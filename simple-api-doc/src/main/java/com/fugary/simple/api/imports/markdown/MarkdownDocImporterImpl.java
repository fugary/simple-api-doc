package com.fugary.simple.api.imports.markdown;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.imports.ApiDocImporter;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.utils.SchemaYamlUtils;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.exports.ApiDocParseUtils;
import com.fugary.simple.api.web.vo.exports.ExportApiDocVo;
import com.fugary.simple.api.web.vo.exports.ExportApiFolderVo;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectInfoVo;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectVo;
import com.fugary.simple.api.web.vo.imports.ApiProjectImportVo;
import com.fugary.simple.api.web.vo.imports.DocSourceData;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.Pair;
import com.fugary.simple.api.service.apidoc.asset.DocAssetStorageService;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Markdown 文档与多级文件夹导入解析器
 *
 * @author gary.fu
 */
@Slf4j
@Component
public class MarkdownDocImporterImpl implements ApiDocImporter {

    @Autowired(required = false)
    private DocAssetStorageService docAssetStorageService;

    public static final Pattern FRONTMATTER_PATTERN = Pattern.compile("^---\\r?\\n(.*?)\\r?\\n---\\r?\\n(.*)$", Pattern.DOTALL);
    public static final Pattern H1_PATTERN = Pattern.compile("(?m)^#\\s+(.+)$");
    public static final Pattern HEADING_PATTERN = Pattern.compile("(?m)^#{1,6}\\s+(.+)$");
    public static final Pattern NUMERIC_PREFIX_PATTERN = Pattern.compile("^(\\d+)[\\.\\-_ ]+(.*)$");
    public static final Pattern SWAGGER_YAML_PATTERN = Pattern.compile("(?m)^\\s*['\"]?(openapi|swagger)['\"]?\\s*:");

    public static final List<String> MD_EXTENSIONS = List.of(".md", ".markdown");
    public static final List<String> IGNORE_PATHS = List.of("__MACOSX", ".DS_Store", ".git/", ".svn/", ".idea/", ".vscode/");

    @Override
    public String getType() {
        return ApiDocConstants.SOURCE_TYPE_MARKDOWN;
    }

    @Override
    public boolean isSupport(String type) {
        return ApiDocConstants.SOURCE_TYPE_MARKDOWN.equalsIgnoreCase(type) || ApiDocConstants.DOC_TYPE_MD.equalsIgnoreCase(type);
    }

    @Override
    public boolean match(DocSourceData sourceData) {
        if (sourceData == null || sourceData.isEmpty()) {
            return false;
        }
        // 1. 二进制 ZIP 格式特征直接判定（PK 魔数 0x50, 0x4B 或 .zip 后缀）
        if (sourceData.isBinary()) {
            byte[] bytes = sourceData.getBinaryContent();
            if (bytes != null && bytes.length >= 4 && bytes[0] == 0x50 && bytes[1] == 0x4B) {
                return true;
            }
            return StringUtils.isNotBlank(sourceData.getFileName()) && sourceData.getFileName().toLowerCase().endsWith(".zip");
        }
        // 2. 文本格式判定（包括 Base64 格式的 ZIP、Virtual JSON 列表、排除 Swagger 等）
        String text = sourceData.getTextContent();
        if (StringUtils.isBlank(text)) {
            return false;
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("UEsDB")) {
            return true;
        }
        if (trimmed.startsWith("[") && trimmed.contains("\"content\"") &&
                (trimmed.contains("\"path\"") || trimmed.contains("\"fileName\"") || trimmed.contains("\"filePath\""))) {
            return true;
        }
        if (trimmed.startsWith("{")) {
            return false;
        }
        if (SWAGGER_YAML_PATTERN.matcher(trimmed).find() || (trimmed.contains("paths:") && trimmed.contains("info:"))) {
            return false;
        }
        return true;
    }

    @Override
    public ExportApiProjectVo doImport(DocSourceData sourceData, ApiProjectImportVo importVo) {
        if (sourceData == null || sourceData.isEmpty()) {
            return null;
        }
        String defaultFileName = importVo != null && StringUtils.isNotBlank(importVo.getFileName())
                ? importVo.getFileName() : StringUtils.defaultIfBlank(sourceData.getFileName(), "README.md");

        Map<String, String> fileMap;
        if (sourceData.isBinary()) {
            // 直接解压二进制字节流，完全无需 Base64 解码中间消耗
            fileMap = extractFromZipBytes(sourceData.getBinaryContent());
        } else {
            fileMap = extractMarkdownFiles(sourceData.getTextContent(), defaultFileName);
        }
        if (fileMap == null || fileMap.isEmpty()) {
            return null;
        }

        ExportApiProjectVo projectVo = new ExportApiProjectVo();
        projectVo.setStatus(ApiDocConstants.STATUS_ENABLED);
        projectVo.setProjectCode(SimpleModelUtils.uuid());
        projectVo.setDocs(new ArrayList<>());
        projectVo.setFolders(new ArrayList<>());

        List<ExportApiFolderVo> allFolders = new ArrayList<>();
        int fileIndex = 0;

        for (Map.Entry<String, String> entry : fileMap.entrySet()) {
            String path = entry.getKey();
            String content = entry.getValue();

            int lastSlash = path.lastIndexOf(ApiDocConstants.FOLDER_PATH_SEPARATOR);
            String folderPath = lastSlash > 0 ? path.substring(0, lastSlash) : "";
            String fileName = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
            String baseName = removeExtension(fileName);
            Matcher numPrefixM = NUMERIC_PREFIX_PATTERN.matcher(baseName);
            boolean hasNumericPrefix = numPrefixM.matches();

            // 1. 解析 Frontmatter
            String body = content;
            Map<String, Object> frontmatter = null;
            Matcher fmM = FRONTMATTER_PATTERN.matcher(content);
            if (fmM.matches()) {
                String yamlStr = fmM.group(1);
                body = fmM.group(2);
                try {
                    frontmatter = SchemaYamlUtils.fromYaml(yamlStr, Map.class, false);
                } catch (Exception e) {
                    log.debug("解析 Frontmatter 异常: {}", e.getMessage());
                }
            }

            // 2. 提取标题 Title
            String title = null;
            if (frontmatter != null) {
                title = Objects.toString(ObjectUtils.firstNonNull(frontmatter.get("title"), frontmatter.get("name")), null);
            }
            if (StringUtils.isBlank(title)) {
                Matcher h1M = H1_PATTERN.matcher(body);
                if (h1M.find()) {
                    title = h1M.group(1).trim();
                }
            }
            if (StringUtils.isBlank(title)) {
                Matcher headM = HEADING_PATTERN.matcher(body);
                if (headM.find()) {
                    title = headM.group(1).trim();
                }
            }
            if (StringUtils.isBlank(title)) {
                title = baseName;
            }

            // 3. 提取排序 SortId
            Integer sortId = null;
            if (frontmatter != null) {
                Object orderObj = ObjectUtils.firstNonNull(frontmatter.get("order"), frontmatter.get("sort"), frontmatter.get("sortId"), frontmatter.get("index"));
                if (orderObj != null) {
                    sortId = NumberUtils.toInt(orderObj.toString(), 0);
                }
            }
            if (sortId == null) {
                if (isReadmeOrIndex(fileName)) {
                    sortId = 1;
                } else if (hasNumericPrefix) {
                    sortId = NumberUtils.toInt(numPrefixM.group(1), 0) * 100;
                }
            }
            if (sortId == null) {
                sortId = (fileIndex + 1) * 100;
            }

            // 4. 其他 Frontmatter 元数据
            String description = null;
            Boolean deprecated = null;
            Boolean locked = null;
            if (frontmatter != null) {
                description = Objects.toString(ObjectUtils.firstNonNull(frontmatter.get("description"), frontmatter.get("summary")), null);
                if (frontmatter.containsKey("deprecated")) {
                    deprecated = Boolean.parseBoolean(Objects.toString(frontmatter.get("deprecated")));
                }
                if (frontmatter.containsKey("locked")) {
                    locked = Boolean.parseBoolean(Objects.toString(frontmatter.get("locked")));
                }
            }

            // 5. 构建 ExportApiDocVo
            ExportApiDocVo docVo = new ExportApiDocVo();
            docVo.setDocType(ApiDocConstants.DOC_TYPE_MD);
            docVo.setDocKey(path);
            docVo.setDocName(title);
            docVo.setSummary(title);
            docVo.setDocContent(body);
            docVo.setDescription(description);
            docVo.setDeprecated(deprecated);
            docVo.setLocked(locked);
            docVo.setSortId(sortId);
            docVo.setStatus(ApiDocConstants.STATUS_ENABLED);

            // 6. 归属到文件夹或根目录
            if (StringUtils.isBlank(folderPath)) {
                projectVo.getDocs().add(docVo);
                if (isReadmeOrIndex(fileName) && StringUtils.isBlank(projectVo.getProjectName())) {
                    projectVo.setProjectName(title);
                }
            } else {
                Pair<ExportApiFolderVo, ExportApiFolderVo> folderPair = calcMarkdownFolder(allFolders, folderPath);
                ExportApiFolderVo folder = folderPair.getLeft();
                folder.getDocs().add(docVo);
            }

            fileIndex++;
        }

        // 整理一级顶级文件夹
        projectVo.setFolders(allFolders.stream()
                .filter(folder -> Objects.isNull(folder.getParentFolder()))
                .collect(Collectors.toList()));

        if (StringUtils.isBlank(projectVo.getProjectName())) {
            projectVo.setProjectName("Markdown Documentation");
        }

        // 基本信息配置
        ExportApiProjectInfoVo projectInfo = new ExportApiProjectInfoVo();
        projectInfo.setSourceType(ApiDocConstants.SOURCE_TYPE_MARKDOWN);
        projectInfo.setSpecVersion(ApiDocConstants.SOURCE_TYPE_MARKDOWN);
        projectInfo.setVersion("1.0.0");
        projectInfo.setStatus(ApiDocConstants.STATUS_ENABLED);
        projectInfo.setDefaultFlag(true);
        projectVo.setProjectInfo(projectInfo);

        return projectVo;
    }

    /**
     * 提取 Markdown 文件列表
     *
     * @param data
     * @param defaultFileName 默认文件名（单文件导入时使用）
     * @return
     */
    protected Map<String, String> extractMarkdownFiles(String data, String defaultFileName) {
        if (StringUtils.isBlank(data)) {
            return Collections.emptyMap();
        }
        byte[] zipBytes = tryDecodeZip(data);
        if (zipBytes != null) {
            return extractFromZipBytes(zipBytes);
        }

        String trimmed = data.trim();
        if (trimmed.startsWith("[") && trimmed.contains("\"content\"")) {
            try {
                List<Map<String, Object>> list = JsonUtils.fromJson(trimmed, new TypeReference<>() {});
                if (list != null && !list.isEmpty()) {
                    Map<String, String> files = new LinkedHashMap<>();
                    for (Map<String, Object> item : list) {
                        String path = Objects.toString(ObjectUtils.firstNonNull(item.get("path"), item.get("fileName"), item.get("filePath")), "");
                        String content = Objects.toString(item.get("content"), "");
                        if (StringUtils.isNotBlank(path)) {
                            files.put(normalizePath(path), content);
                        }
                    }
                    if (!files.isEmpty()) {
                        return stripCommonRootDirectory(files);
                    }
                }
            } catch (Exception e) {
                log.debug("非 JSON 数组格式: {}", e.getMessage());
            }
        }

        // 单个文件导入
        Map<String, String> files = new LinkedHashMap<>();
        String entryPath = StringUtils.defaultIfBlank(defaultFileName, "README.md");
        files.put(normalizePath(entryPath), data);
        return files;
    }

    protected byte[] tryDecodeZip(String data) {
        String trimmed = data.trim();
        try {
            if (trimmed.startsWith("UEsDB") || isZipBase64(trimmed)) {
                byte[] bytes = Base64.getDecoder().decode(trimmed);
                if (bytes.length >= 4 && bytes[0] == 0x50 && bytes[1] == 0x4B) {
                    return bytes;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    protected boolean isZipBase64(String str) {
        if (str.length() < 8 || str.length() % 4 != 0) {
            return false;
        }
        return str.matches("^[A-Za-z0-9+/=\\r\\n]+$");
    }

    protected Map<String, String> extractFromZipBytes(byte[] zipBytes) {
        Map<String, String> files = extractFromZipWithFallback(zipBytes, StandardCharsets.UTF_8, Charset.forName("GBK"), Charset.defaultCharset());
        return stripCommonRootDirectory(files);
    }

    protected Map<String, String> extractFromZipWithFallback(byte[] zipBytes, Charset... charsets) {
        for (Charset charset : charsets) {
            try {
                Map<String, String> files = tryExtractZip(zipBytes, charset);
                if (!files.isEmpty()) {
                    return files;
                }
            } catch (Exception e) {
                log.debug("使用编码 {} 解压 ZIP 失败: {}", charset, e.getMessage());
            }
        }
        return Collections.emptyMap();
    }

    protected Map<String, String> tryExtractZip(byte[] zipBytes, Charset charset) throws Exception {
        Map<String, String> markdownFiles = new LinkedHashMap<>();
        Map<String, String> imagePathToUrlMap = new HashMap<>();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes), charset)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (!entry.isDirectory() && !shouldIgnore(name)) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = zis.read(buffer)) != -1) {
                        baos.write(buffer, 0, len);
                    }
                    byte[] contentBytes = baos.toByteArray();

                    if (isMarkdownFile(name)) {
                        String content = decodeContent(contentBytes, charset);
                        markdownFiles.put(normalizePath(name), content);
                    } else if (docAssetStorageService != null && docAssetStorageService.isImageFile(name)) {
                        String localUrl = docAssetStorageService.saveImage(contentBytes, name, "zip_import");
                        if (StringUtils.isNotBlank(localUrl)) {
                            String normName = normalizePath(name);
                            imagePathToUrlMap.put(normName, localUrl);
                            imagePathToUrlMap.put(FilenameUtils.getName(normName), localUrl);
                        }
                    }
                }
                zis.closeEntry();
            }
        }

        if (docAssetStorageService != null && !imagePathToUrlMap.isEmpty()) {
            Map<String, String> replacedFiles = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : markdownFiles.entrySet()) {
                String replacedContent = docAssetStorageService.replaceRelativeImages(entry.getValue(), entry.getKey(), imagePathToUrlMap);
                replacedFiles.put(entry.getKey(), replacedContent);
            }
            return replacedFiles;
        }

        return markdownFiles;
    }

    protected String decodeContent(byte[] contentBytes, Charset entryCharset) {
        String utf8Str = new String(contentBytes, StandardCharsets.UTF_8);
        if (!StandardCharsets.UTF_8.equals(entryCharset) && utf8Str.contains("\uFFFD")) {
            return new String(contentBytes, entryCharset);
        }
        return utf8Str;
    }

    protected Map<String, String> stripCommonRootDirectory(Map<String, String> files) {
        if (files.size() <= 1) {
            return files;
        }
        String firstKey = files.keySet().iterator().next();
        int slashIndex = firstKey.indexOf(ApiDocConstants.FOLDER_PATH_SEPARATOR);
        if (slashIndex <= 0) {
            return files;
        }
        String rootDir = firstKey.substring(0, slashIndex + 1);
        boolean allMatch = files.keySet().stream().allMatch(k -> k.startsWith(rootDir));
        if (allMatch) {
            Map<String, String> result = new LinkedHashMap<>();
            files.forEach((k, v) -> result.put(k.substring(rootDir.length()), v));
            return stripCommonRootDirectory(result);
        }
        return files;
    }

    protected boolean shouldIgnore(String path) {
        if (StringUtils.isBlank(path)) {
            return true;
        }
        for (String ignore : IGNORE_PATHS) {
            if (path.contains(ignore)) {
                return true;
            }
        }
        return false;
    }

    protected boolean isMarkdownFile(String path) {
        if (StringUtils.isBlank(path)) {
            return false;
        }
        String lower = path.toLowerCase();
        return MD_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    protected String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String p = path.replace('\\', '/').trim();
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        return p;
    }

    protected String removeExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        for (String ext : MD_EXTENSIONS) {
            if (fileName.toLowerCase().endsWith(ext)) {
                return fileName.substring(0, fileName.length() - ext.length());
            }
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    protected boolean isReadmeOrIndex(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return false;
        }
        String base = removeExtension(fileName);
        return "README".equalsIgnoreCase(base) || "index".equalsIgnoreCase(base);
    }

    /**
     * 计算并创建多级 Markdown 目录层级（复用 ApiDocParseUtils.calcApiPathFolder 并扩展序号解析）
     *
     * @param existsFolders 已解析文件夹列表
     * @param folderPath 相对目录路径（如 01-guide/02-advanced）
     * @return left: 当前底层目录, right: 顶层目录
     */
    public static Pair<ExportApiFolderVo, ExportApiFolderVo> calcMarkdownFolder(List<ExportApiFolderVo> existsFolders, String folderPath) {
        int sizeBefore = existsFolders.size();
        Pair<ExportApiFolderVo, ExportApiFolderVo> result = ApiDocParseUtils.calcApiPathFolder(existsFolders, folderPath);
        // 对新增的文件夹应用序号前缀解析提取排序
        for (int i = sizeBefore; i < existsFolders.size(); i++) {
            ExportApiFolderVo folder = existsFolders.get(i);
            folder.setFolderCode(folder.getFolderName());
            Matcher numM = NUMERIC_PREFIX_PATTERN.matcher(folder.getFolderName());
            if (numM.matches()) {
                folder.setSortId(NumberUtils.toInt(numM.group(1), 0) * 100);
            }
        }
        return result;
    }
}
