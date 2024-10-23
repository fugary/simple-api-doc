package com.fugary.simple.api.exports.openapi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.*;
import com.fugary.simple.api.exports.ApiDocExporter;
import com.fugary.simple.api.service.apidoc.ApiDocSchemaService;
import com.fugary.simple.api.service.apidoc.ApiFolderService;
import com.fugary.simple.api.service.apidoc.ApiProjectInfoDetailService;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.utils.SchemaJsonUtils;
import com.fugary.simple.api.web.vo.exports.ExportEnvConfigVo;
import com.fugary.simple.api.web.vo.exports.ExtendMarkdownFile;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectInfoDetailVo;
import com.fugary.simple.api.web.vo.query.ProjectDetailQueryVo;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Consumer;
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
    @Autowired
    private ApiFolderService apiFolderService;

    @Override
    public OpenAPI export(Integer projectId, List<Integer> docIds) {
        ProjectDetailQueryVo queryVo = ProjectDetailQueryVo.builder()
                .projectId(projectId)
                .includeDocs(true)
                .forceEnabled(true)
                .build();
        ApiProjectDetailVo detailVo = apiProjectService.loadProjectVo(queryVo);
        ApiProjectInfo projectInfo = detailVo.getInfoList().get(0);
        List<ApiDoc> docList = detailVo.getDocs();
        if (CollectionUtils.isNotEmpty(docIds)) { // 过滤指定文档
            docList = docList.stream().filter(apiDoc -> docIds.contains(apiDoc.getId()))
                    .collect(Collectors.toList());
        }
        // 加载文档详情
        List<ApiDocDetailVo> docDetailList = apiDocSchemaService.loadDetailList(docList);
        // 加载项目schema和security数据
        List<ApiProjectInfoDetail> apiInfoDetails = apiProjectInfoDetailService.loadByProject(projectId,
                Set.of(ApiDocConstants.PROJECT_SCHEMA_TYPE_COMPONENT, ApiDocConstants.PROJECT_SCHEMA_TYPE_SECURITY));
        // 提取和文档相关的schema和security数据
        ApiProjectInfoDetailVo projectInfoDetailVo = apiProjectInfoDetailService.parseInfoDetailVo(projectInfo, apiInfoDetails, docDetailList);
        // 解析文件夹，方便后续读取
        Map<Integer, ApiFolder> folderMap = detailVo.getFolders().stream().collect(Collectors.toMap(ApiFolder::getId, Function.identity()));
        Pair<Map<String, ApiFolder>, Map<Integer, String>> folderMapPair = apiFolderService.calcFolderMap(detailVo.getFolders());
        // 新建OpenAPI数据
        OpenAPI openAPI = new OpenAPI(SpecVersion.valueOf(projectInfo.getSpecVersion()))
                .openapi(projectInfo.getOasVersion())
                .components(new Components())
                .paths(new Paths())
                .servers(new ArrayList<>())
                .info(new Info().title(detailVo.getProjectName())
                        .summary(detailVo.getProjectName())
                        .version(projectInfo.getVersion()));
        processComponentsAndSecuritySchemas(projectInfoDetailVo, openAPI);
        List<ExtendMarkdownFile> markdownFiles = new ArrayList<>();
        Set<Tag> tags = new LinkedHashSet<>();
        for (ApiDocDetailVo apiDocDetail : docDetailList) {
            ApiFolder apiFolder = folderMap.get(apiDocDetail.getFolderId());
            if (apiFolder != null) { // 文件夹必须存在
                if (ApiDocConstants.DOC_TYPE_API.equals(apiDocDetail.getDocType())) { // 接口处理
                    String urlPath = apiDocDetail.getUrl();
                    openAPI.getPaths().addPathItem(urlPath, calcPathItem(openAPI, folderMapPair, apiFolder, apiDocDetail));
                    tags.add(new Tag().name(apiFolder.getFolderName())
                            .description(apiFolder.getDescription())); // 提取文件夹信息作为Tag
                } else if (ApiDocConstants.DOC_TYPE_MD.equals(apiDocDetail.getDocType())) { // markdown处理
                    if (StringUtils.equals(ApiDocConstants.DOC_KEY_PREFIX + "openapi-info", apiDocDetail.getDocKey())) {
                        openAPI.getInfo().description(apiDocDetail.getDocContent());
                    } else if (apiFolder.getParentId() == null) { // 根目录
                        ExtendMarkdownFile markdownFile = new ExtendMarkdownFile();
                        markdownFile.setFileName(apiDocDetail.getDocKey());
                        markdownFile.setTitle(apiDocDetail.getDocName());
                        markdownFile.setSortId(apiDocDetail.getSortId());
                        markdownFile.setContent(apiDocDetail.getDocContent());
                        markdownFiles.add(markdownFile);
                    }
                }
            }
        }
        if (!markdownFiles.isEmpty()) {
            openAPI.addExtension(ApiDocConstants.X_SIMPLE_MARKDOWN_FILES, markdownFiles);
        }
        processServerItems(detailVo, openAPI);
        openAPI.setTags(List.copyOf(tags));
        return openAPI;
    }

    private PathItem calcPathItem(OpenAPI openAPI, Pair<Map<String, ApiFolder>, Map<Integer, String>> folderMapPair, ApiFolder apiFolder, ApiDocDetailVo apiDocDetail) {
        Map<Integer, String> folderPathMap = folderMapPair.getRight();
        PathItem pathItem = new PathItem();
        Map<String, Consumer<Operation>> pathFunctions = getPathFunctions(pathItem);
        Operation operation = new Operation().addTagsItem(apiFolder.getFolderName())
                .summary(apiDocDetail.getSummary())
                .operationId(apiDocDetail.getOperationId())
                .description(apiDocDetail.getDescription());
        ApiDocSchema parametersSchema = apiDocDetail.getParametersSchema();
        if (parametersSchema != null && StringUtils.isNotBlank(parametersSchema.getSchemaContent())) {
            List<Parameter> parameters = SchemaJsonUtils.fromJson(parametersSchema.getSchemaContent(), new TypeReference<>() {
            }, SchemaJsonUtils.isV31(openAPI));
            operation.parameters(parameters);
        }
        if (!apiDocDetail.getRequestsSchemas().isEmpty()) {
            RequestBody requestBody = new RequestBody();
            requestBody.setContent(new Content());
            apiDocDetail.getRequestsSchemas().stream().filter(schema -> StringUtils.isNotBlank(schema.getSchemaContent()))
                    .forEach(schema -> requestBody.getContent().addMediaType(schema.getContentType(),
                            SchemaJsonUtils.fromJson(schema.getSchemaContent(), MediaType.class, SchemaJsonUtils.isV31(openAPI))));
            operation.requestBody(requestBody);
        }
        if (!apiDocDetail.getResponsesSchemas().isEmpty()) {
            ApiResponses apiResponses = new ApiResponses();
            apiDocDetail.getResponsesSchemas().stream().filter(schema -> StringUtils.isNotBlank(schema.getSchemaContent()))
                    .forEach(schema -> {
                        ApiResponse apiResponse = new ApiResponse();
                        apiResponse.setContent(new Content());
                        apiResponse.setDescription(schema.getDescription());
                        apiResponse.getContent().addMediaType(schema.getContentType(),
                                SchemaJsonUtils.fromJson(schema.getSchemaContent(), MediaType.class, SchemaJsonUtils.isV31(openAPI)));
                        apiResponses.addApiResponse(schema.getSchemaName(), apiResponse);
                    });
            operation.responses(apiResponses);
        }
        String fullFolderPath = folderPathMap.get(apiFolder.getId());
        if (StringUtils.isNotBlank(fullFolderPath) && fullFolderPath.contains(ApiDocConstants.FOLDER_PATH_SEPARATOR)) {
            String folderPath = fullFolderPath.substring(fullFolderPath.indexOf(ApiDocConstants.FOLDER_PATH_SEPARATOR) + 1);
            if (StringUtils.contains(folderPath, ApiDocConstants.FOLDER_PATH_SEPARATOR)) {
                operation.addExtension(ApiDocConstants.X_SIMPLE_FOLDER, folderPath);
                operation.addExtension(ApiDocConstants.X_APIFOX_FOLDER, folderPath);
            }
        }
        pathFunctions.get(StringUtils.upperCase(apiDocDetail.getMethod())).accept(operation);
        return pathItem;
    }

    protected Map<String, Consumer<Operation>> getPathFunctions(PathItem pathItem) {
        return Map.of(
                HttpMethod.GET.name(), pathItem::setGet,
                HttpMethod.HEAD.name(), pathItem::setHead,
                HttpMethod.POST.name(), pathItem::setPost,
                HttpMethod.PUT.name(), pathItem::setPut,
                HttpMethod.PATCH.name(), pathItem::setPatch,
                HttpMethod.DELETE.name(), pathItem::setDelete,
                HttpMethod.OPTIONS.name(), pathItem::setOptions,
                HttpMethod.TRACE.name(), pathItem::setTrace
        );
    }

    /**
     * 处理已保存的Server信息
     *
     * @param detailVo
     * @param openAPI
     */
    protected void processServerItems(ApiProjectDetailVo detailVo, OpenAPI openAPI) {
        detailVo.getInfoList().stream().filter(info -> StringUtils.isNotBlank(info.getEnvContent())).forEach(info -> {
            List<ExportEnvConfigVo> envList = SchemaJsonUtils.fromJson(info.getEnvContent(), new TypeReference<>() {
            }, SchemaJsonUtils.isV31(openAPI));
            for (ExportEnvConfigVo envVo : envList) {
                Server server = new Server().url(envVo.getUrl()).description(envVo.getName());
                if (!openAPI.getServers().contains(server)) {
                    openAPI.addServersItem(server);
                }
            }
        });
    }

    /**
     * 处理保存的Security和Component信息
     *
     * @param projectInfoDetailVo
     * @param openAPI
     */
    protected void processComponentsAndSecuritySchemas(ApiProjectInfoDetailVo projectInfoDetailVo, OpenAPI openAPI) {
        List<ApiProjectInfoDetail> componentSchemas = projectInfoDetailVo.getComponentSchemas();
        List<ApiProjectInfoDetail> securitySchemas = projectInfoDetailVo.getSecuritySchemas();
        componentSchemas.forEach(detail -> {
            Schema<?> schema = SchemaJsonUtils.fromJson(detail.getSchemaContent(), Schema.class, SchemaJsonUtils.isV31(openAPI));
            openAPI.getComponents().addSchemas(detail.getSchemaName(), schema);
        });
        securitySchemas.forEach(detail -> {
            Map<String, SecurityScheme> secSchemas = SchemaJsonUtils.fromJson(detail.getSchemaContent(), new TypeReference<>() {
            }, SchemaJsonUtils.isV31(openAPI));
            openAPI.getComponents().setSecuritySchemes(secSchemas);
        });
    }
}
