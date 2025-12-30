package com.fugary.simple.api.utils.exports;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiFolder;
import com.fugary.simple.api.entity.api.ApiProjectInfoDetail;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.web.vo.exports.*;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectInfoDetailVo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Create date 2024/9/27<br>
 *
 * @author gary.fu
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiDocParseUtils {

    // 注册 shutdown hook
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Path tempPath = getApiTempDir().toPath();
                if (Files.exists(tempPath)) {
                    Files.walk(tempPath).sorted(Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            log.error("删除临时文件失败", e);
                        }
                    });
                }
            } catch (IOException e) {
                log.error("删除临时文件失败", e);
            }
        }));
    }

    /**
     * 以已存在文件夹为基准计算文件夹层级
     *
     * @param existsFolders 已解析的文件夹列表
     * @param folderPath    文件夹路径 a/b/c/d
     * @return left——底层目录，right——顶层目录
     */
    public static Pair<ExportApiFolderVo, ExportApiFolderVo> calcApiPathFolder(List<ExportApiFolderVo> existsFolders, String folderPath) {
        Map<String, ExportApiFolderVo> folderMap = existsFolders.stream().collect(Collectors.toMap(ExportApiFolderVo::getFolderPath, Function.identity()));
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
            addFolderIfNotExist(existsFolders, childFolder);
            currentParentFolder = childFolder; // 把当前folder记为parent
            if (i == 0) {
                topFolder = childFolder;
            }
        }
        // 新增或获取Folder信息
        return Pair.of(currentParentFolder, topFolder); // 应该永远有个folder，不能为空
    }

    /**
     * 是否存在判断
     *
     * @param folders
     * @param folder
     */
    public static void addFolderIfNotExist(List<ExportApiFolderVo> folders, ExportApiFolderVo folder) {
        if (folder != null && folders.stream().noneMatch(cFolder -> StringUtils.equals(cFolder.getFolderPath(), folder.getFolderPath()))) {
            if (folder.getSortId() == null) {
                int size = folders.size();
                folder.setSortId(size * 100 + 10);
            }
            folders.add(folder);
        }
    }

    /**
     * 处理已经存在的Schema
     *
     * @param apiDocVo
     * @param existsDocDetail
     */
    public static boolean processExistsSchemas(ExportApiDocVo apiDocVo, ApiDocDetailVo existsDocDetail) {
        boolean isChanged = false;
        if (existsDocDetail != null) {
            ExportApiDocSchemaVo securityRequirements = apiDocVo.getSecurityRequirements();
            ApiProjectInfoDetail existsSecurityRequirements = existsDocDetail.getSecurityRequirements();
            if (mergeApiDocSchema(securityRequirements, existsSecurityRequirements)) {
                isChanged = true;
            }
            ExportApiDocSchemaVo parametersSchema = apiDocVo.getParametersSchema();
            ApiProjectInfoDetail existsParametersSchema = existsDocDetail.getParametersSchema();
            if (mergeApiDocSchema(parametersSchema, existsParametersSchema)) {
                isChanged = true;
            }
            List<ExportApiDocSchemaVo> requestsSchemas = apiDocVo.getRequestsSchemas();
            Map<String, ApiProjectInfoDetail> requestSchemaMap = toSchemaMap(existsDocDetail.getRequestsSchemas());
            for (ExportApiDocSchemaVo requestsSchema : requestsSchemas) {
                ApiProjectInfoDetail existsRequestSchema = requestSchemaMap.get(getApiDocSchemaKey(requestsSchema));
                if (mergeApiDocSchema(requestsSchema, existsRequestSchema)) {
                    isChanged = true;
                }
            }
            List<ExportApiDocSchemaVo> responsesSchemas = apiDocVo.getResponsesSchemas();
            Map<String, ApiProjectInfoDetail> responseSchemaMap = toSchemaMap(existsDocDetail.getResponsesSchemas());
            for (ExportApiDocSchemaVo responsesSchema : responsesSchemas) {
                ApiProjectInfoDetail existsResponseSchema = responseSchemaMap.get(getApiDocSchemaKey(responsesSchema));
                if (mergeApiDocSchema(responsesSchema, existsResponseSchema)) {
                    isChanged = true;
                }
            }
        }
        return isChanged;
    }

    private static Map<String, ApiProjectInfoDetail> toSchemaMap(List<ApiProjectInfoDetail> list) {
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyMap();
        }
        return list.stream().collect(Collectors.toMap(ApiDocParseUtils::getApiDocSchemaKey, Function.identity(), (existing, replacement) -> replacement));
    }

    private static boolean mergeApiDocSchema(ExportApiDocSchemaVo apiDocSchema, ApiProjectInfoDetail existsApiDocSchema) {
        boolean isChanged = isApiDocSchemaChanged(apiDocSchema, existsApiDocSchema);
        if (apiDocSchema != null && existsApiDocSchema != null) {
            if (isChanged) {
                SimpleModelUtils.mergeAuditInfo(apiDocSchema, existsApiDocSchema);
            } else {
                SimpleModelUtils.mergeCreateInfo(apiDocSchema, existsApiDocSchema);
            }
        }
        return isChanged;
    }

    public static boolean isApiDocSchemaChanged(ExportApiDocSchemaVo apiDocSchema, ApiProjectInfoDetail existsApiDocSchema) {
        if (apiDocSchema == null && existsApiDocSchema == null) {
            return false;
        }
        return apiDocSchema == null || existsApiDocSchema == null
                || !StringUtils.equals(apiDocSchema.getSchemaContent(), existsApiDocSchema.getSchemaContent())
                || !StringUtils.equals(apiDocSchema.getExamples(), existsApiDocSchema.getExamples());
    }

    public static String getApiDocSchemaKey(ApiProjectInfoDetail s){
        return String.join("|", s.getSchemaName(), s.getContentType(), s.getBodyType());
    }

    public static String getProjectInfoDetailKey(ApiProjectInfoDetail infoDetail) {
        return String.join("|", infoDetail.getBodyType(), infoDetail.getSchemaName());
    }

    public static Pair<ExportApiProjectInfoDetailVo, ApiProjectInfoDetail> processProjectInfoDetail(Map<String, ApiProjectInfoDetail> detailsMap,
                                                ExportApiProjectInfoDetailVo projectInfoDetailVo, boolean isV31) {
        ApiProjectInfoDetail existsInfoDetail = detailsMap.get(ApiDocParseUtils.getProjectInfoDetailKey(projectInfoDetailVo));
        if (existsInfoDetail != null) {
            boolean isSameInfoDetail = SimpleModelUtils.isSameData(projectInfoDetailVo, existsInfoDetail, "schemaContent")
                    && ApiSchemaContentUtils.isSameSchemaContent(projectInfoDetailVo.getSchemaContent(), existsInfoDetail.getSchemaContent());
            if (ApiDocConstants.PROJECT_SCHEMA_TYPE_COMPONENT.equals(existsInfoDetail.getBodyType())) {
                if (Boolean.TRUE.equals(existsInfoDetail.getLocked())) {
                    String mergedSchemaContent = ApiSchemaContentUtils.mergeComponentSchemaContent(existsInfoDetail.getSchemaContent(),
                            projectInfoDetailVo.getSchemaContent(), isV31);
                    projectInfoDetailVo.setSchemaContent(mergedSchemaContent);
                    projectInfoDetailVo.setLocked(existsInfoDetail.getLocked());
                }
            }
            projectInfoDetailVo.setVersion(existsInfoDetail.getVersion());
            projectInfoDetailVo.setId(existsInfoDetail.getId()); // 存在的话更新对应的ID
            SimpleModelUtils.mergeAuditInfo(projectInfoDetailVo, existsInfoDetail);
            if (isSameInfoDetail) { // 数据相同不更新
                return Pair.of(null, null);
            }
        }
        return Pair.of(projectInfoDetailVo, existsInfoDetail);
    }

    public static void overrideApiDocModifyInfo(ApiProjectInfoDetailVo projectInfoDetailVo, ApiDoc apiDoc) {
        // 查找修改时间最近的一条数据，
        Stream.concat(projectInfoDetailVo.getComponentSchemas().stream(), Stream.of(projectInfoDetailVo.getSecuritySchemas(),
                        projectInfoDetailVo.getSecurityRequirements())).filter(Objects::nonNull)
                .filter(detail -> StringUtils.isNotBlank(detail.getModifier()) && detail.getModifyDate() != null)
                .max(Comparator.comparing(ApiProjectInfoDetail::getModifyDate)).stream().findFirst().ifPresent(detail -> {
                    Date docDate = ObjectUtils.defaultIfNull(apiDoc.getModifyDate(), apiDoc.getCreateDate());
                    if (docDate == null || docDate.before(detail.getModifyDate())) {
                        apiDoc.setModifier(detail.getModifier());
                        apiDoc.setModifyDate(detail.getModifyDate());
                    }
                });
    }

    /**
     * 相同url保留一份
     *
     * @param envConfigs
     * @return
     */
    public static List<ExportEnvConfigVo> distinctEnvConfigs(List<ExportEnvConfigVo> envConfigs) {
        List<ExportEnvConfigVo> results = new ArrayList<>();
        if (envConfigs != null) {
            for (ExportEnvConfigVo envConfig : envConfigs) {
                int index = SimpleModelUtils.indexOf(results, envConfig, Comparator.comparing(ExportEnvConfigVo::getUrl));
                if (index > -1) {
                    results.set(index, envConfig);
                } else {
                    results.add(envConfig);
                }
            }
        }
        return results;
    }

    public static List<ExportEnvConfigVo> mergeEnvConfigs(List<ExportEnvConfigVo> savedEnvConfigs, List<ExportEnvConfigVo> envConfigs) {
        for (ExportEnvConfigVo envConfig : envConfigs) {
            int index = SimpleModelUtils.indexOf(savedEnvConfigs, envConfig, Comparator.comparing(ExportEnvConfigVo::getUrl));
            if (index > -1) {
                SimpleModelUtils.copyNoneNullValue(savedEnvConfigs.get(index), envConfig);
            }
        }
        savedEnvConfigs.removeIf(savedConfig -> !Boolean.TRUE.equals(savedConfig.getManual()));
        savedEnvConfigs.addAll(0, envConfigs);
        return savedEnvConfigs;
    }

    public static List<ExportEnvConfigVo> mergeEnvConfigs(String savedEnvConfigStr, String envConfigStr) {
        TypeReference<List<ExportEnvConfigVo>> typeReference = new TypeReference<>() {};
        List<ExportEnvConfigVo> savedEnvConfigs = StringUtils.isNotBlank(savedEnvConfigStr)? JsonUtils.fromJson(savedEnvConfigStr, typeReference): new ArrayList<>();
        List<ExportEnvConfigVo> envConfigs = StringUtils.isNotBlank(envConfigStr)? JsonUtils.fromJson(envConfigStr, typeReference): new ArrayList<>();
        return mergeEnvConfigs(savedEnvConfigs, envConfigs);
    }

    public static List<ExportEnvConfigVo> getFilteredEnvConfigs(String totalContent, String filterContent) {
        if (StringUtils.isNotBlank(totalContent)) {
            List<ExportEnvConfigVo> envList = JsonUtils.fromJson(totalContent, new TypeReference<>() {
            });
            envList = getEnabledEnvConfigs(envList);
            if (StringUtils.isNotBlank(filterContent)) {
                List<ExportEnvConfigVo> filterList = JsonUtils.fromJson(filterContent, new TypeReference<>() {
                });
                Set<String> enabledUrls = envList.stream().map(ExportEnvConfigVo::getUrl).collect(Collectors.toSet());
                envList = getEnabledEnvConfigs(filterList).stream().filter(env -> enabledUrls.contains(env.getUrl()))
                        .collect(Collectors.toList());
            }
            return envList;
        }
        return new ArrayList<>();
    }

    public static List<ExportEnvConfigVo> getEnabledEnvConfigs(List<ExportEnvConfigVo> envList) {
        return envList.stream().filter(env -> !Boolean.TRUE.equals(env.getDisabled()))
                .filter(env -> StringUtils.isNotBlank(env.getName()) && StringUtils.isNotBlank(env.getUrl()))
                .collect(Collectors.toList());
    }

    /**
     * 临时文件夹
     *
     * @return
     */
    public static File getApiTempDir() {
        return new File(FileUtils.getTempDirectoryPath(), "gen-openapi-output");
    }

    /**
     * 计算当前服务器全路径
     *
     * @param request
     * @param path
     * @return
     */
    public static String getCurrentUrlPath(HttpServletRequest request, String path) {
        String baseUrl = ServletUriComponentsBuilder.fromRequestUri(request)
                .replacePath(null)
                .build()
                .toUriString();
        return baseUrl + path;
    }

    /**
     * docKey计算
     *
     * @param operationId
     * @param url
     * @param method
     * @return
     */
    public static String calcApiDocKey(String operationId, String url, String method) {
        return StringUtils.defaultIfBlank(operationId, url + "#" + method);
    }

    /**
     * 计算新的docKey
     * @param newDoc
     * @param apiFolder
     */
    public static void calcNewDocKey(ApiDoc newDoc, ApiFolder apiFolder) {
        if (ApiDocConstants.DOC_TYPE_API.equals(newDoc.getDocType())) {
            newDoc.setOperationId(StringUtils.defaultIfBlank(newDoc.getOperationId(), SimpleModelUtils.uuid()));
            String docKey = ApiDocParseUtils.calcApiDocKey(newDoc.getOperationId(), newDoc.getUrl(), newDoc.getMethod());
            if (apiFolder != null) {
                docKey = apiFolder.getFolderName() + "#" + docKey;
            }
            newDoc.setDocKey(docKey);
        } else if (StringUtils.isBlank(newDoc.getDocKey())) {
            newDoc.setDocKey(SimpleModelUtils.uuid());
        }
    }
}
