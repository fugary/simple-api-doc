package com.fugary.simple.api.imports.swagger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.imports.ApiDocImporter;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.utils.SchemaJsonUtils;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.exports.ApiDocParseUtils;
import com.fugary.simple.api.web.vo.exports.*;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.tags.Tag;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SwaggerImporterImpl implements ApiDocImporter {

    public SwaggerImporterImpl() {
    }

    @Override
    public boolean isSupport(String type) {
        return "openapi".equals(type);
    }

    @Override
    public ExportApiProjectVo doImport(String data) {
        ParseOptions parseOptions = new ParseOptions();
        parseOptions.setResolve(true);
        SwaggerParseResult result = new OpenAPIParser().readContents(data, null, parseOptions);
        OpenAPI openAPI = result.getOpenAPI();
        ExportApiProjectVo projectVo = null;
        if (openAPI != null) {
            Map<String, List<Triple<String, PathItem, List<Pair<String, Operation>>>>> pathMap = openAPI.getPaths().entrySet().stream().map(entry -> {
                PathItem pathItem = entry.getValue();
                List<Pair<String, Operation>> operations = getAllOperationsInAPath(pathItem);
                return Triple.of(entry.getKey(), entry.getValue(), operations);
            }).collect(Collectors.groupingBy(triple -> {
                List<Pair<String, Operation>> operations = triple.getRight();
                Pair<String, Operation> firstOptionPair = operations.get(0);
                return getTagName0(firstOptionPair.getRight().getTags());
            }, LinkedHashMap::new, Collectors.toList()));
            projectVo = new ExportApiProjectVo();
            projectVo.setStatus(ApiDocConstants.STATUS_ENABLED);
            projectVo.setProjectCode(SimpleModelUtils.uuid());
            processFolders(openAPI, projectVo, pathMap);
            processProjectInfo(openAPI, projectVo, data);
        }
        return projectVo;
    }

    protected void processProjectInfo(OpenAPI openAPI, ExportApiProjectVo projectVo, String content) {
        processApiInfo(openAPI, projectVo);
        processApiContent(openAPI, projectVo, content);
        processApiSecurity(openAPI, projectVo);
        processApiComponents(openAPI, projectVo);
        processApiMarkdownFiles(openAPI, projectVo);
    }

    protected void processApiContent(OpenAPI openAPI, ExportApiProjectVo projectVo, String content) {
        ExportApiProjectInfoDetailVo detailVo = new ExportApiProjectInfoDetailVo();
        detailVo.setBodyType(ApiDocConstants.PROJECT_SCHEMA_TYPE_CONTENT);
        detailVo.setSchemaContent(content);
        detailVo.setStatus(ApiDocConstants.STATUS_ENABLED);
        detailVo.setDescription(Optional.ofNullable(openAPI.getInfo()).map(Info::getDescription).orElse(null));
        projectVo.getProjectInfoDetails().add(detailVo);
    }

    protected void processApiSecurity(OpenAPI openAPI, ExportApiProjectVo projectVo) {
        Components components = openAPI.getComponents();
        if (components != null && components.getSecuritySchemes() != null && !components.getSecuritySchemes().isEmpty()) {
            ExportApiProjectInfoDetailVo detailVo = new ExportApiProjectInfoDetailVo();
            detailVo.setBodyType(ApiDocConstants.PROJECT_SCHEMA_TYPE_SECURITY);
            detailVo.setSchemaContent(SchemaJsonUtils.toJson(components.getSecuritySchemes(), SchemaJsonUtils.isV31(openAPI)));
            detailVo.setStatus(ApiDocConstants.STATUS_ENABLED);
            projectVo.getProjectInfoDetails().add(detailVo);
        }
        if (CollectionUtils.isNotEmpty(openAPI.getSecurity())) {
            ExportApiProjectInfoDetailVo detailVo = new ExportApiProjectInfoDetailVo();
            detailVo.setBodyType(ApiDocConstants.SCHEMA_TYPE_SECURITY_REQUIREMENT);
            detailVo.setSchemaContent(SchemaJsonUtils.toJson(openAPI.getSecurity(), SchemaJsonUtils.isV31(openAPI)));
            detailVo.setStatus(ApiDocConstants.STATUS_ENABLED);
            projectVo.getProjectInfoDetails().add(detailVo);
        }
    }

    protected void processApiInfo(OpenAPI openAPI, ExportApiProjectVo projectVo) {
        ExportApiProjectInfoVo projectInfo = new ExportApiProjectInfoVo();
        projectInfo.setStatus(ApiDocConstants.STATUS_ENABLED);
        projectInfo.setOasVersion(openAPI.getOpenapi());
        projectInfo.setSpecVersion(openAPI.getSpecVersion().name());
        projectInfo.setEnvContent(JsonUtils.toJson(ApiDocParseUtils.distinctEnvConfigs(openAPI.getServers()
                .stream().filter(server -> StringUtils.isNotBlank(server.getUrl()) && StringUtils.isNotBlank(server.getDescription())).map(server -> {
            String url = server.getUrl();
            String name = server.getDescription();
            return new ExportEnvConfigVo(name, url, null, null);
        }).collect(Collectors.toList()))));
        Info info = openAPI.getInfo();
        if (info != null) {
            projectVo.setProjectName(info.getTitle());
            projectVo.setDescription(StringUtils.defaultIfBlank(info.getTitle(), info.getDescription()));
            projectInfo.setVersion(info.getVersion());
            if (StringUtils.isNotBlank(info.getTitle()) && StringUtils.isNotBlank(info.getDescription()) && StringUtils.length(info.getDescription()) > 80) { // markdown
                ExportApiDocVo doc = new ExportApiDocVo();
                doc.setDocType(ApiDocConstants.DOC_TYPE_MD);
                doc.setDocKey(ApiDocConstants.DOC_KEY_PREFIX + "openapi-info");
                doc.setDocName("接口说明");
                doc.setSortId(10);
                doc.setDocContent(info.getDescription());
                doc.setStatus(ApiDocConstants.STATUS_ENABLED);
                projectVo.getDocs().add(doc);
            }
        }
        projectVo.setProjectInfo(projectInfo);
    }

    protected void processApiComponents(OpenAPI openAPI, ExportApiProjectVo projectVo) {
        if (openAPI.getComponents() != null && openAPI.getComponents().getSchemas() != null) {
            openAPI.getComponents().getSchemas().forEach((s, schema) -> {
                ExportApiProjectInfoDetailVo detail = new ExportApiProjectInfoDetailVo();
                detail.setBodyType(ApiDocConstants.PROJECT_SCHEMA_TYPE_COMPONENT);
                detail.setSchemaName(s);
                detail.setSchemaContent(SchemaJsonUtils.toJson(schema, SchemaJsonUtils.isV31(openAPI)));
                detail.setDescription(schema.getDescription());
                detail.setStatus(ApiDocConstants.STATUS_ENABLED);
                projectVo.getProjectInfoDetails().add(detail);
            });
        }
    }

    protected void processApiMarkdownFiles(OpenAPI openAPI, ExportApiProjectVo projectVo) {
        Object markdownFilesObj;
        if (openAPI.getExtensions() != null && (markdownFilesObj = openAPI.getExtensions().get(ApiDocConstants.X_SIMPLE_MARKDOWN_FILES)) != null) {
            String markdownFileStr = markdownFilesObj instanceof String ? (String) markdownFilesObj : SchemaJsonUtils.toJson(markdownFilesObj, SchemaJsonUtils.isV31(openAPI));
            List<ExtendMarkdownFile> markdownFiles = JsonUtils.fromJson(markdownFileStr, new TypeReference<>() {
            });
            for (int i = 0; i < markdownFiles.size(); i++) {
                ExtendMarkdownFile markdownFile = markdownFiles.get(i);
                ExportApiFolderVo folder = null;
                if (StringUtils.isNotBlank(markdownFile.getFolderName())) {
                    Pair<ExportApiFolderVo, ExportApiFolderVo> pathFolderPair = ApiDocParseUtils.calcApiPathFolder(projectVo.getFolders(), markdownFile.getFolderName());
                    folder = pathFolderPair.getKey();
                }
                ExportApiDocVo doc = new ExportApiDocVo();
                doc.setDocType(ApiDocConstants.DOC_TYPE_MD);
                doc.setDocKey(markdownFile.getFileName());
                doc.setDocName(markdownFile.getTitle());
                doc.setDocContent(markdownFile.getContent());
                doc.setStatus(ApiDocConstants.STATUS_ENABLED);
                if (markdownFile.getSortId() != null) {
                    doc.setSortId(markdownFile.getSortId() + 10);
                } else {
                    doc.setSortId((i + 1) * 10);
                }
                if (folder == null) {
                    projectVo.getDocs().add(doc); // 根目录
                } else {
                    folder.getDocs().add(doc); // 子目录
                }
            }
        }
    }

    /**
     * 解析文件夹信息
     *
     * @param openAPI
     * @param projectVo
     * @param pathMap
     */
    protected void processFolders(OpenAPI openAPI, ExportApiProjectVo projectVo,
                                  Map<String, List<Triple<String, PathItem, List<Pair<String, Operation>>>>> pathMap) {
        List<ExportApiFolderVo> folders = projectVo.getFolders();
        AtomicInteger sortId = new AtomicInteger(10000);
        pathMap.forEach((path, tripleList) -> {
            for (Triple<String, PathItem, List<Pair<String, Operation>>> triple : tripleList) {
                String url = triple.getLeft();
                for (Pair<String, Operation> operationPair : triple.getRight()) {
                    Pair<ExportApiFolderVo, ExportApiFolderVo> operationFolderPair = getOperationFolder(openAPI, folders, operationPair);
                    ExportApiFolderVo folder = operationFolderPair.getLeft();
                    if (folder != null) {
                        ExportApiDocVo apiDocVo = calcApiDoc(openAPI, folder, url, operationPair);
                        apiDocVo.setSortId(sortId.addAndGet(100));
                        folder.getDocs().add(apiDocVo);
                    }
                }
            }
        });
        projectVo.setFolders(folders.stream().filter(folder -> Objects.isNull(folder.getParentFolder()))
                .collect(Collectors.toList()));
    }

    /**
     * 解析Api文档信息
     *
     * @param openAPI
     * @param folder
     * @param url
     * @param operationPair
     * @return
     */
    protected ExportApiDocVo calcApiDoc(OpenAPI openAPI, ExportApiFolderVo folder, String url, Pair<String, Operation> operationPair) {
        String method = operationPair.getLeft();
        Operation operation = operationPair.getRight();
        ExportApiDocVo apiDocVo = new ExportApiDocVo();
        apiDocVo.setMethod(method);
        apiDocVo.setOperationId(operation.getOperationId());
        String docName = StringUtils.defaultIfBlank(operation.getSummary(), operation.getOperationId());
        apiDocVo.setSummary(docName);
        apiDocVo.setDocName(docName);
        apiDocVo.setDocType(ApiDocConstants.DOC_TYPE_API);
        apiDocVo.setUrl(url);
        String docKey = StringUtils.defaultIfBlank(operation.getOperationId(), url + "#" + method);
        if (folder != null) {
            docKey = folder.getFolderName() + "#" + docKey;
        }
        apiDocVo.setDocKey(docKey);
        apiDocVo.setStatus(ApiDocConstants.STATUS_ENABLED);
        apiDocVo.setDescription(operation.getDescription());
        calcDocSchemas(openAPI, apiDocVo, operation);
        return apiDocVo;
    }

    /**
     * 解析请求响应等参数信息
     *
     * @param apiDoc
     * @param operation
     */
    protected void calcDocSchemas(OpenAPI openAPI, ExportApiDocVo apiDoc, Operation operation) {
        // 处理Security列表
        if (CollectionUtils.isNotEmpty(operation.getSecurity())) {
            ExportApiDocSchemaVo securityRequirements = new ExportApiDocSchemaVo();
            securityRequirements.setBodyType(ApiDocConstants.SCHEMA_TYPE_SECURITY_REQUIREMENT);
            securityRequirements.setSchemaContent(SchemaJsonUtils.toJson(operation.getSecurity(), SchemaJsonUtils.isV31(openAPI)));
            securityRequirements.setStatus(ApiDocConstants.STATUS_ENABLED);
            apiDoc.setSecurityRequirements(securityRequirements);
        }
        // 处理参数列表
        if (CollectionUtils.isNotEmpty(operation.getParameters())) {
            ExportApiDocSchemaVo parametersSchema = new ExportApiDocSchemaVo();
            parametersSchema.setBodyType(ApiDocConstants.DOC_SCHEMA_TYPE_PARAMETERS);
            parametersSchema.setSchemaContent(SchemaJsonUtils.toJson(operation.getParameters(), SchemaJsonUtils.isV31(openAPI)));
            parametersSchema.setStatus(ApiDocConstants.STATUS_ENABLED);
            apiDoc.setParametersSchema(parametersSchema);
        }
        // 处理请求
        if (operation.getRequestBody() != null && MapUtils.isNotEmpty(operation.getRequestBody().getContent())) {
            operation.getRequestBody().getContent().forEach((contentType, mediaType) -> {
                if (mediaType != null) {
                    ExportApiDocSchemaVo requestBodySchema = new ExportApiDocSchemaVo();
                    requestBodySchema.setBodyType(ApiDocConstants.DOC_SCHEMA_TYPE_REQUEST);
                    requestBodySchema.setContentType(contentType);
                    requestBodySchema.setSchemaContent(SchemaJsonUtils.toJson(mediaType, SchemaJsonUtils.isV31(openAPI)));
                    requestBodySchema.setStatus(ApiDocConstants.STATUS_ENABLED);
                    Schema schema = mediaType.getSchema();
                    requestBodySchema.setDescription(operation.getRequestBody().getDescription());
                    if (schema != null) {
                        requestBodySchema.setSchemaName(schema.getName());
                    }
                    List<Example> examples = getExamples(mediaType);
                    if (CollectionUtils.isNotEmpty(examples)) {
                        requestBodySchema.setExamples(SchemaJsonUtils.toJson(examples, SchemaJsonUtils.isV31(openAPI)));
                    }
                    apiDoc.getRequestsSchemas().add(requestBodySchema);
                }
            });
        }
        // 处理响应
        if (operation.getResponses() != null) {
            operation.getResponses().forEach((responseCode, response) -> {
                if (MapUtils.isNotEmpty(response.getContent())) {
                    response.getContent().forEach((contentType, mediaType) -> {
                        ExportApiDocSchemaVo responseSchema = new ExportApiDocSchemaVo();
                        responseSchema.setSchemaName(responseCode);
                        responseSchema.setDescription(response.getDescription());
                        responseSchema.setBodyType(ApiDocConstants.DOC_SCHEMA_TYPE_RESPONSE);
                        responseSchema.setContentType(contentType);
                        responseSchema.setSchemaContent(SchemaJsonUtils.toJson(mediaType, SchemaJsonUtils.isV31(openAPI)));
                        responseSchema.setStatus(ApiDocConstants.STATUS_ENABLED);
                        List<Example> examples = getExamples(mediaType);
                        if (CollectionUtils.isNotEmpty(examples)) {
                            responseSchema.setExamples(SchemaJsonUtils.toJson(examples, SchemaJsonUtils.isV31(openAPI)));
                        }
                        responseSchema.setStatusCode(calcStatusCode(responseCode).value());
                        apiDoc.getResponsesSchemas().add(responseSchema);
                    });
                } else {
                    ExportApiDocSchemaVo responseSchema = new ExportApiDocSchemaVo();
                    responseSchema.setSchemaName(responseCode);
                    responseSchema.setDescription(response.getDescription());
                    responseSchema.setBodyType(ApiDocConstants.DOC_SCHEMA_TYPE_RESPONSE);
                    responseSchema.setStatus(ApiDocConstants.STATUS_ENABLED);
                    responseSchema.setStatusCode(calcStatusCode(responseCode).value());
                    apiDoc.getResponsesSchemas().add(responseSchema);
                }
            });
        }
    }

    protected HttpStatus calcStatusCode(String responseCode) {
        if (NumberUtils.isDigits(responseCode)) {
            HttpStatus statusCode = HttpStatus.resolve(NumberUtils.toInt(responseCode));
            if (statusCode != null) {
                return statusCode;
            }
        }
        return HttpStatus.OK;
    }

    protected List<Example> getExamples(io.swagger.v3.oas.models.media.MediaType mediaType){
        List<Example> examples = new ArrayList<>();
        if (mediaType != null) {
            if (mediaType.getExamples() != null) {
                examples.addAll(mediaType.getExamples().values());
            }
            if (mediaType.getExample() != null) {
                examples.add(new Example().summary("Example").value(mediaType.getExample()));
            }
        }
        return examples;
    }

    protected String getTagName0(List<String> tags) {
        return CollectionUtils.isNotEmpty(tags) ? tags.get(0) : ApiDocConstants.SIMPLE_EMPTY_PATH_FOLDER_ALIAS;
    }

    protected Pair<ExportApiFolderVo, ExportApiFolderVo> getOperationFolder(OpenAPI openAPI, List<ExportApiFolderVo> folders, Pair<String, Operation> operationPair) {
        // 获取Operation所在的Folder
        Operation operation = operationPair.getRight();
        String folderPath = getTagName0(operation.getTags());
        if (operation.getExtensions() != null) {
            folderPath = StringUtils.defaultIfBlank((String) operation.getExtensions().get(ApiDocConstants.X_APIFOX_FOLDER), folderPath);
            folderPath = StringUtils.defaultIfBlank((String) operation.getExtensions().get(ApiDocConstants.X_SIMPLE_FOLDER), folderPath);
        }
        Pair<ExportApiFolderVo, ExportApiFolderVo> parsedPair = ApiDocParseUtils.calcApiPathFolder(folders, folderPath);
        ExportApiFolderVo childFolder = parsedPair.getLeft();
        findOperationTag(openAPI, operation).ifPresent(tag -> {
            childFolder.setDescription(tag.getDescription());
        });
        // 新增或获取Folder信息
        return parsedPair; // 应该永远有个folder，不能为空
    }

    protected Optional<Tag> findOperationTag(OpenAPI openAPI, Operation operation) {
        if (openAPI.getTags() != null) {
            return openAPI.getTags().stream().filter(tag -> operation.getTags() != null
                    && operation.getTags().contains(tag.getName())).findFirst();
        }
        return Optional.empty();
    }

    protected List<Pair<String, Operation>> getAllOperationsInAPath(PathItem pathObj) {
        List<Pair<String, Operation>> operations = new ArrayList<>();
        addToOperationsList(operations, pathObj.getGet(), PathItem.HttpMethod.GET.name());
        addToOperationsList(operations, pathObj.getPut(), PathItem.HttpMethod.PUT.name());
        addToOperationsList(operations, pathObj.getPost(), PathItem.HttpMethod.POST.name());
        addToOperationsList(operations, pathObj.getPatch(), PathItem.HttpMethod.PATCH.name());
        addToOperationsList(operations, pathObj.getDelete(), PathItem.HttpMethod.DELETE.name());
        addToOperationsList(operations, pathObj.getTrace(), PathItem.HttpMethod.TRACE.name());
        addToOperationsList(operations, pathObj.getOptions(), PathItem.HttpMethod.OPTIONS.name());
        addToOperationsList(operations, pathObj.getHead(), PathItem.HttpMethod.HEAD.name());
        return operations;
    }

    protected void addToOperationsList(List<Pair<String, Operation>> operationsList, Operation operation, String method) {
        if (operation == null) {
            return;
        }
        operationsList.add(Pair.of(method, operation));
    }
}
