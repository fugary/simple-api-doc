package com.fugary.simple.api.exports.openapi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiFolder;
import com.fugary.simple.api.entity.api.ApiProjectInfoDetail;
import com.fugary.simple.api.exports.ApiDocExporter;
import com.fugary.simple.api.service.apidoc.ApiDocSchemaService;
import com.fugary.simple.api.service.apidoc.ApiProjectInfoDetailService;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.utils.SchemaJsonUtils;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectInfoDetailVo;
import com.fugary.simple.api.web.vo.query.ProjectDetailQueryVo;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Create date 2024/10/23<br>
 *
 * @author gary.fu
 */
@Component
public class OpenApiApiDocExporterImpl implements ApiDocExporter<OpenAPI> {

    @Autowired
    private ApiProjectService apiProjectService;
    @Autowired
    private ApiProjectInfoDetailService apiProjectInfoDetailService;
    @Autowired
    private ApiDocSchemaService apiDocSchemaService;

    @Override
    public OpenAPI export(Integer projectId, List<Integer> docIds) {
        ProjectDetailQueryVo queryVo = ProjectDetailQueryVo.builder()
                .projectId(projectId)
                .includeDocs(true)
                .forceEnabled(true)
                .build();
        ApiProjectDetailVo detailVo = apiProjectService.loadProjectVo(queryVo);
        List<ApiDoc> docList = detailVo.getDocs();
        if (CollectionUtils.isNotEmpty(docIds)) {
            docList = docList.stream().filter(apiDoc -> docIds.contains(apiDoc.getId()))
                    .collect(Collectors.toList());
        }
        List<ApiDocDetailVo> docDetailList = apiDocSchemaService.loadDetailList(docList);
        List<ApiProjectInfoDetail> apiInfoDetails = apiProjectInfoDetailService.loadByProject(projectId,
                Set.of(ApiDocConstants.PROJECT_SCHEMA_TYPE_COMPONENT, ApiDocConstants.PROJECT_SCHEMA_TYPE_SECURITY));
        ApiProjectInfoDetailVo projectInfoDetailVo = apiProjectInfoDetailService.parseInfoDetailVo(detailVo.getInfoList().get(0), apiInfoDetails, docDetailList);
        OpenAPI openAPI = new OpenAPI();
        openAPI.setComponents(new Components());
        List<ApiProjectInfoDetail> componentSchemas = projectInfoDetailVo.getComponentSchemas();
        List<ApiProjectInfoDetail> securitySchemas = projectInfoDetailVo.getSecuritySchemas();
        componentSchemas.forEach(detail -> {
            Schema<?> schema = SchemaJsonUtils.fromJson(detail.getSchemaContent(), Schema.class);
            openAPI.getComponents().addSchemas(detail.getSchemaName(), schema);
        });
        securitySchemas.forEach(detail -> {
            Map<String, SecurityScheme> secSchemas = SchemaJsonUtils.fromJson(detail.getSchemaContent(), new TypeReference<>() {
            });
            openAPI.getComponents().setSecuritySchemes(secSchemas);
        });
        Map<Integer, ApiFolder> folderMap = detailVo.getFolders().stream().collect(Collectors.toMap(ApiFolder::getId, Function.identity()));
        Set<Tag> tags = new LinkedHashSet<>();
        for (ApiDoc apiDoc : docList) {
            if (ApiDocConstants.DOC_TYPE_API.equals(apiDoc.getDocType())) {
                ApiFolder apiFolder = folderMap.get(apiDoc.getFolderId());
                if (apiFolder != null) {
                    tags.add(new Tag().name(apiFolder.getFolderName())
                            .description(apiFolder.getDescription()));
                }
            }
        }
        openAPI.setTags(List.copyOf(tags));
        return openAPI;
    }
}
