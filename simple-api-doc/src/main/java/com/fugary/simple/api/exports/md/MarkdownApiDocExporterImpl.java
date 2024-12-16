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
import com.fugary.simple.api.service.apidoc.ApiDocSchemaService;
import com.fugary.simple.api.service.apidoc.ApiProjectInfoDetailService;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.utils.SimpleModelUtils;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private ApiDocSchemaService apiDocSchemaService;
    @Autowired
    private ApiDocViewGenerator apiDocViewGenerator;
    @Autowired
    private Configuration freemarkerConfig; // FreeMarker 自动配置的 Configuration

    @Override
    public String export(Integer projectId, List<Integer> docIds) {
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
        List<ApiDocDetailVo> docDetailList = apiDocSchemaService.loadDetailList(docList);
        // 加载项目schema和security数据
        List<ApiProjectInfoDetail> apiInfoDetails = apiProjectInfoDetailService.loadByProject(projectId, ApiDocConstants.PROJECT_SCHEMA_TYPES);
        ApiProjectInfo projectInfo = findApiProjectInfo(detailVo, infoIds);
        // 提取和文档相关的schema和security数据
        ApiProjectInfoDetailVo projectInfoDetailVo = apiProjectInfoDetailService.parseInfoDetailVo(projectInfo, apiInfoDetails, docDetailList);
        MdViewContext context = new MdViewContext();
        context.setGenerateComponents(false);
        SpecVersion specVersion = projectInfo != null ? SpecVersion.valueOf(projectInfo.getSpecVersion()) : SpecVersion.V30;
        Map<String, Schema<?>> schemasMap = new LinkedHashMap<>();
        for (ApiDocDetailVo apiDocDetail : docDetailList) {
            if (ApiDocConstants.DOC_TYPE_API.equals(apiDocDetail.getDocType())) {
                context.setApiDocDetail(apiDocDetail);
                apiDocDetail.setProjectInfoDetail(projectInfoDetailVo);
                SimpleModelUtils.processComponents(apiDocDetail, specVersion, schemasMap);
                String apiMarkdown = apiDocViewGenerator.generate(context);
                apiDocDetail.setApiMarkdown(apiMarkdown);
            }
        }
        // 设置数据
        Map<String, Object> model = new HashMap<>();
        model.put("apiProject", detailVo);
        model.put("schemasMap", schemasMap);
        model.put("apiDocs", docDetailList);
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
     * 获取一个ProjectInfo信息对象
     *
     * @param detailVo
     * @param infoIds
     * @return
     */
    protected ApiProjectInfo findApiProjectInfo(ApiProjectDetailVo detailVo, Set<Integer> infoIds) {
        ApiProjectInfo projectInfo;
        if (detailVo.getInfoList().isEmpty()) {
            projectInfo = SimpleModelUtils.getDefaultProjectInfo(detailVo);
        } else {
            projectInfo = detailVo.getInfoList().stream().filter(info -> infoIds.contains(info.getId())).findFirst()
                    .orElseGet(() -> detailVo.getInfoList().get(0));
        }
        return projectInfo;
    }

}
