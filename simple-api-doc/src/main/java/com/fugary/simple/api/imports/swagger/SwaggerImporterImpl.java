package com.fugary.simple.api.imports.swagger;

import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.imports.ApiDocImporter;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.web.vo.exports.*;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.tags.Tag;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
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
        if (openAPI != null && openAPI.getTags() != null) {
            Map<String, List<Triple<String, PathItem, List<Pair<String, Operation>>>>> pathMap = openAPI.getPaths().entrySet().stream().map(entry -> {
                PathItem pathItem = entry.getValue();
                List<Pair<String, Operation>> operations = getAllOperationsInAPath(pathItem);
                return Triple.of(entry.getKey(), entry.getValue(), operations);
            }).collect(Collectors.groupingBy(triple -> {
                List<Pair<String, Operation>> operations = triple.getRight();
                Pair<String, Operation> firstOptionPair = operations.get(0);
                return firstOptionPair.getRight().getTags().get(0);
            }, LinkedHashMap::new, Collectors.toList()));
            projectVo = new ExportApiProjectVo();
            projectVo.setStatus(ApiDocConstants.STATUS_ENABLED);
            processProjectSchema(openAPI, projectVo, data);
            processFolders(openAPI, projectVo, pathMap);
        }
        return projectVo;
    }

    protected void processProjectSchema(OpenAPI openAPI, ExportApiProjectVo projectVo, String content) {
        ExportApiProjectSchemaVo projectSchema = new ExportApiProjectSchemaVo();
        projectVo.setProjectSchema(projectSchema);
        projectSchema.setStatus(ApiDocConstants.STATUS_ENABLED);
        projectSchema.setOasVersion(openAPI.getOpenapi());
        projectSchema.setSpecVersion(openAPI.getSpecVersion().name());
        projectSchema.setEnvContent(JsonUtils.toJson(openAPI.getServers().stream().map(server -> {
            String url = server.getUrl();
            String name = server.getDescription();
            return new ExportEnvConfigVo(name, url);
        }).collect(Collectors.toList())));
        ExportApiProjectSchemaDetailVo detailVo = new ExportApiProjectSchemaDetailVo();
        detailVo.setBodyType(ApiDocConstants.PROJECT_SCHEMA_TYPE_CONTENT);
        detailVo.setSchemaContent(content);
        projectVo.getProjectSchemaDetails().add(detailVo);
        Info info = openAPI.getInfo();
        if (info != null) {
            projectVo.setDescription(info.getTitle());
            detailVo.setDescription(info.getDescription());
            projectSchema.setVersion(info.getVersion());
            if (StringUtils.containsIgnoreCase(info.getDescription(), "## ")) { // markdown
                ExportApiDocVo doc = new ExportApiDocVo();
                doc.setDocType(ApiDocConstants.DOC_TYPE_MD);
                doc.setDocName("接口说明");
                doc.setDocContent(info.getDescription());
                projectVo.getDocs().add(doc);
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
        List<ExportApiFolderVo> folders = new ArrayList<>();
        pathMap.forEach((path, tripleList) -> {
            for (Triple<String, PathItem, List<Pair<String, Operation>>> triple : tripleList) {
                String url = triple.getLeft();
                for (Pair<String, Operation> operationPair : triple.getRight()) {
                    Pair<ExportApiFolderVo, ExportApiFolderVo> operationFolderPair = getOperationFolder(openAPI, folders, operationPair);
                    ExportApiFolderVo folder = operationFolderPair.getLeft();
                    if (folder != null) {
                        folder.getDocs().add(calcApiDoc(url, operationPair));
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
     * @param url
     * @param operationPair
     * @return
     */
    protected ExportApiDocVo calcApiDoc(String url, Pair<String, Operation> operationPair) {
        String method = operationPair.getLeft();
        Operation operation = operationPair.getRight();
        ExportApiDocVo apiDocVo = new ExportApiDocVo();
        apiDocVo.setMethod(method);
        apiDocVo.setOperationId(operation.getOperationId());
        apiDocVo.setSummary(operation.getSummary());
        apiDocVo.setDocName(operation.getSummary());
        apiDocVo.setDocType(ApiDocConstants.DOC_TYPE_API);
        apiDocVo.setUrl(url);
        apiDocVo.setStatus(ApiDocConstants.STATUS_ENABLED);
        calcDocSchemas(apiDocVo, operation);
        return apiDocVo;
    }

    /**
     * 解析请求响应等参数信息
     *
     * @param apiDoc
     * @param operation
     */
    protected void calcDocSchemas(ExportApiDocVo apiDoc, Operation operation) {
        // 处理参数列表
        if (operation.getParameters() != null) {
            ExportApiDocSchemaVo parametersSchema = new ExportApiDocSchemaVo();
            parametersSchema.setBodyType(ApiDocConstants.DOC_SCHEMA_TYPE_PARAMETERS);
            parametersSchema.setSchemaContent(JsonUtils.toJson(operation.getParameters()));
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
                    requestBodySchema.setSchemaContent(JsonUtils.toJson(mediaType));
                    requestBodySchema.setStatus(ApiDocConstants.STATUS_ENABLED);
                    Schema schema = mediaType.getSchema();
                    if (schema != null) {
                        requestBodySchema.setSchemaName(schema.getName());
                        requestBodySchema.setDescription(schema.getDescription());
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
                        responseSchema.setBodyType(ApiDocConstants.DOC_SCHEMA_TYPE_RESPONSE);
                        responseSchema.setContentType(contentType);
                        responseSchema.setSchemaContent(JsonUtils.toJson(mediaType));
                        responseSchema.setStatus(ApiDocConstants.STATUS_ENABLED);
                        Schema schema = mediaType.getSchema();
                        if (schema != null) {
                            responseSchema.setSchemaName(schema.getName());
                            responseSchema.setDescription(schema.getDescription());
                        }
                        apiDoc.getResponsesSchemas().add(responseSchema);
                    });
                }
            });
        }
    }

    protected Pair<ExportApiFolderVo, ExportApiFolderVo> getOperationFolder(OpenAPI openAPI, List<ExportApiFolderVo> folders, Pair<String, Operation> operationPair) {
        // 获取Operation所在的Folder
        Operation operation = operationPair.getRight();
        String folderPath = operation.getTags().get(0);
        if (operation.getExtensions() != null) {
            folderPath = StringUtils.defaultIfBlank((String) operation.getExtensions().get(ApiDocConstants.X_APIFOX_FOLDER), folderPath);
            folderPath = StringUtils.defaultIfBlank((String) operation.getExtensions().get(ApiDocConstants.X_SIMPLE_FOLDER), folderPath);
        }
        Map<String, ExportApiFolderVo> folderMap = folders.stream().collect(Collectors.toMap(ExportApiFolderVo::getFolderPath, Function.identity()));
        String[] folderNames = StringUtils.split(folderPath, ApiDocConstants.FOLDER_PATH_SEPARATOR);
        ExportApiFolderVo topFolder = null;
        ExportApiFolderVo currentParentFolder = null;
        for (int i = 0; i < folderNames.length; i++) {
            String folderName = folderNames[i];
            List<String> namePaths = new ArrayList<>();
            for (int j = 0; j <= i; j++) {
                namePaths.add(folderNames[j]);
            }
            String childFolderPath = StringUtils.join(namePaths, ApiDocConstants.FOLDER_PATH_SEPARATOR);
            ExportApiFolderVo childFolder = folderMap.computeIfAbsent(childFolderPath, k -> {
                ExportApiFolderVo folder = new ExportApiFolderVo();
                folder.setFolderPath(childFolderPath);
                folder.setFolderName(folderName);
                folder.setStatus(ApiDocConstants.STATUS_ENABLED);
                return folder;
            });
            if (currentParentFolder != null) {
                addFolderIfNotExist(currentParentFolder.getFolders(), childFolder);
                if (childFolder.getParentFolder() == null) {
                    childFolder.setParentFolder(currentParentFolder);
                }
            }
            addFolderIfNotExist(folders, childFolder);
            currentParentFolder = childFolder; // 把当前folder记为parent
            if (i == 0) {
                topFolder = childFolder;
            }
            if (i == folderNames.length - 1) {
                findOperationTag(openAPI, operation).ifPresent(tag -> {
                    childFolder.setDescription(tag.getDescription());
                });
            }
        }
        // 新增或获取Folder信息
        return Pair.of(currentParentFolder, topFolder); // 应该永远有个folder，不能为空
    }

    protected Optional<Tag> findOperationTag(OpenAPI openAPI, Operation operation) {
        if (openAPI.getTags() != null) {
            return openAPI.getTags().stream().filter(tag -> operation.getTags() != null
                    && operation.getTags().contains(tag.getName())).findFirst();
        }
        return Optional.empty();
    }

    protected void addFolderIfNotExist(List<ExportApiFolderVo> folders, ExportApiFolderVo folder) {
        if (folder != null && folders.stream().noneMatch(cFolder -> StringUtils.equals(cFolder.getFolderPath(), folder.getFolderPath()))) {
            folders.add(folder);
        }
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
