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
import com.fugary.simple.api.service.apidoc.ApiProjectInfoDetailService;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.service.apidoc.asset.DocAssetStorageService;
import com.fugary.simple.api.utils.SchemaJsonUtils;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.exports.ApiDocParseUtils;
import com.fugary.simple.api.web.vo.exports.ExportEnvConfigVo;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectInfoDetailVo;
import com.fugary.simple.api.web.vo.query.ProjectDetailQueryVo;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 单个 Markdown 文件导出实现（支持说明文档优先排序、标题动态降级防冲突、本地图片 Base64 内嵌与相对链接锚点重写）
 *
 * @author gary.fu
 */
@Slf4j
@Setter
@Getter
@Component
public class MarkdownApiDocExporterImpl implements ApiDocExporter<String> {

    @Autowired
    private ApiProjectService apiProjectService;
    @Autowired
    private ApiProjectInfoDetailService apiProjectInfoDetailService;
    @Autowired
    private ApiDocViewGenerator apiDocViewGenerator;
    @Autowired(required = false)
    private DocAssetStorageService docAssetStorageService;
    @Autowired
    private Configuration freemarkerConfig; // FreeMarker 自动配置的 Configuration

    @Override
    public String export(Integer projectId, ApiExportFilter exportFilter) {
        List<Integer> docIds = exportFilter.getDocIds();
        ProjectDetailQueryVo queryVo = ProjectDetailQueryVo.builder()
                .projectId(projectId)
                .includeDocs(true)
                .forceEnabled(true)
                .build();
        ApiProjectDetailVo detailVo = apiProjectService.loadProjectVo(queryVo);
        // 解析文件夹，方便后续读取
        Map<Integer, ApiFolder> folderMap = detailVo.getFolders().stream().collect(Collectors.toMap(ApiFolder::getId, Function.identity()));
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
        List<ApiProjectInfoDetailVo> projectInfoDetails = projectInfos.stream().map(projectInfo -> apiProjectInfoDetailService.parseInfoDetailVo(projectInfo, apiInfoDetails, docDetailList)).collect(Collectors.toList());
        // 提取和文档相关的schema和security数据
        ApiProjectInfoDetailVo projectInfoDetailVo = apiProjectInfoDetailService.mergeInfoDetailVo(projectInfoDetails);
        MdViewContext context = new MdViewContext();
        context.setGenerateComponents(false);
        Map<String, Schema<?>> schemasMap = new LinkedHashMap<>();
        context.setSchemasMap(schemasMap);
        // 对 docDetailList 按照树形结构排序（同级目录下 Markdown 说明文档优先置顶，API 接口紧随其后集中展现）
        docDetailList.sort(Comparator.comparing(d -> ApiDocParseUtils.getSingleMdDocSortKey(d, folderMap)));

        // 收集文档目标映射表用于相对链接重写为单文件锚点
        Map<String, String> docTargetMap = buildDocTargetMap(docDetailList);
        boolean shouldEmbedImages = Boolean.TRUE.equals(exportFilter.getEmbedImages());
        Map<String, String> imageCache = new HashMap<>();

        for (ApiDocDetailVo apiDocDetail : docDetailList) {
            List<String> folderNames = ApiDocParseUtils.getFolderNames(apiDocDetail.getFolderId(), folderMap);
            String folderPath = String.join(" / ", folderNames);
            String topLevelFolder = folderNames.isEmpty() ? "" : folderNames.get(0);
            boolean isSubFolder = StringUtils.isNotBlank(folderPath);

            apiDocDetail.setFolderPath(folderPath);
            apiDocDetail.setTopLevelFolder(topLevelFolder);
            if (ApiDocConstants.DOC_TYPE_API.equals(apiDocDetail.getDocType())) {
                SpecVersion specVersion = SchemaJsonUtils.resolveSpecVersion(projectInfoDetailVo != null ? projectInfoDetailVo.getSpecVersion() : null);
                context.setApiDocDetail(apiDocDetail);
                apiDocDetail.setProject(detailVo);
                apiDocDetail.setProjectInfoDetail(projectInfoDetailVo);
                SimpleModelUtils.processComponents(apiDocDetail, specVersion, schemasMap);
                String apiMarkdown = apiDocViewGenerator.generate(context);
                // 子目录下（### 🔗 接口名）：接口内部小节降级1级（### 基本信息 -> #### 基本信息）
                // 根目录下（## 🔗 接口名）：接口内部小节保持3级（### 基本信息），结构清晰自洽
                if (isSubFolder) {
                    apiMarkdown = MarkdownHeadingUtils.demoteHeadings(apiMarkdown, 1);
                }
                // 内联图片为 Base64
                if (shouldEmbedImages) {
                    apiMarkdown = inlineImagesAsBase64(apiMarkdown, detailVo.getProjectCode(), imageCache);
                }
                // 重写相对链接为文档内锚点
                apiMarkdown = MarkdownHeadingUtils.rewriteDocLinks(apiMarkdown, docTargetMap);
                apiDocDetail.setApiMarkdown(apiMarkdown);
            } else {
                String docContent = apiDocDetail.getDocContent();
                // 剥离 Frontmatter、去除重复首行标题，并将内部标题规范降级：子目录为 ####+（从属于 ### 📄 章节），根目录为 ###+（从属于 ## 📄 章节）
                int targetMinLevel = isSubFolder ? 4 : 3;
                docContent = MarkdownHeadingUtils.normalizeDocMarkdown(docContent, apiDocDetail.getDocName(), targetMinLevel);
                // 内联图片为 Base64
                if (shouldEmbedImages) {
                    docContent = inlineImagesAsBase64(docContent, detailVo.getProjectCode(), imageCache);
                }
                // 重写相对链接为文档内锚点
                docContent = MarkdownHeadingUtils.rewriteDocLinks(docContent, docTargetMap);
                apiDocDetail.setDocContent(docContent);
            }
        }
        // 项目描述内联图片
        if (shouldEmbedImages && StringUtils.isNotBlank(detailVo.getDescription())) {
            detailVo.setDescription(inlineImagesAsBase64(detailVo.getDescription(), detailVo.getProjectCode(), imageCache));
        }

        // 排序 schemasMap
        schemasMap = apiDocViewGenerator.sortSchemasMap(schemasMap, context.getDirectSchemaNames());

        // 设置数据
        Map<String, Object> model = new HashMap<>();
        model.put("apiProject", detailVo);
        model.put("schemasMap", schemasMap);
        model.put("apiDocs", docDetailList);
        model.put("apiVersion", StringUtils.defaultIfBlank(detailVo.getApiVersion(), projectInfoDetailVo != null ? projectInfoDetailVo.getVersion() : null));
        if (StringUtils.isNotBlank(detailVo.getEnvContent())) {
            List<ExportEnvConfigVo> envList = ApiDocParseUtils.getFilteredEnvConfigs(detailVo.getEnvContent(), exportFilter.getEnvContent());
            model.put("envList", envList);
        }
        try {
            // 加载模板
            Template template = freemarkerConfig.getTemplate("ExportApiMdView.md.ftl");
            // 渲染模板
            return FreeMarkerTemplateUtils.processTemplateIntoString(template, model);
        } catch (IOException | TemplateException e) {
            log.error("模板渲染失败", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 构建文档相对路径及名称与目标标题的映射表，用于单文档内相对链接转换为锚点跳转
     */
    private Map<String, String> buildDocTargetMap(List<ApiDocDetailVo> docDetailList) {
        Map<String, String> docTargetMap = new HashMap<>();
        for (ApiDocDetailVo doc : docDetailList) {
            String docName = doc.getDocName();
            if (StringUtils.isNotBlank(docName)) {
                docTargetMap.put(docName, docName);
                docTargetMap.put(docName + ".md", docName);
                if (doc.getId() != null) {
                    docTargetMap.put(String.valueOf(doc.getId()), docName);
                }
                if (StringUtils.isNotBlank(doc.getUrl())) {
                    docTargetMap.put(doc.getUrl(), docName);
                    String fileName = FilenameUtils.getName(doc.getUrl());
                    if (StringUtils.isNotBlank(fileName)) {
                        docTargetMap.put(fileName, docName);
                    }
                }
            }
        }
        return docTargetMap;
    }

    /**
     * 扫描并将 Markdown 中的本地图片资源替换为 Base64 Data URL
     *
     * @param content     原始 Markdown 内容
     * @param projectCode 项目 Code
     * @param cache       Base64 缓存，避免同张图片多次重复编码
     * @return 替换后的内容
     */
    protected String inlineImagesAsBase64(String content, String projectCode, Map<String, String> cache) {
        return docAssetStorageService != null ? docAssetStorageService.inlineImagesAsBase64(content, projectCode, cache) : content;
    }
}
