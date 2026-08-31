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
        return calcApiPathFolder(existsFolders, folderPath, null);
    }

    /**
     * 以已存在文件夹为基准计算文件夹层级
     *
     * @param existsFolders 已解析的文件夹列表
     * @param folderPath    文件夹路径 a/b/c/d (展示名称)
     * @param folderCodePath 文件夹编码路径 code1/code2/code3 (编码标识，可选)
     * @return left——底层目录，right——顶层目录
     */
    public static Pair<ExportApiFolderVo, ExportApiFolderVo> calcApiPathFolder(List<ExportApiFolderVo> existsFolders, String folderPath, String folderCodePath) {
        Map<String, ExportApiFolderVo> folderMap = existsFolders.stream().collect(Collectors.toMap(ExportApiFolderVo::getFolderPath, Function.identity(), (existing, replacement) -> existing));
        String[] folderNames = StringUtils.split(folderPath, ApiDocConstants.FOLDER_PATH_SEPARATOR);
        String[] folderCodes = StringUtils.isNotBlank(folderCodePath) ? StringUtils.split(folderCodePath, ApiDocConstants.FOLDER_PATH_SEPARATOR) : null;
        if (folderNames == null || folderNames.length == 0) {
            return Pair.of(null, null);
        }
        ExportApiFolderVo topFolder = null;
        ExportApiFolderVo currentParentFolder = null;
        List<String> codePaths = new ArrayList<>();
        List<String> namePaths = new ArrayList<>();
        for (int i = 0; i < folderNames.length; i++) {
            String folderName = folderNames[i];
            String folderCode = (folderCodes != null && i < folderCodes.length && StringUtils.isNotBlank(folderCodes[i])) ? folderCodes[i] : folderName;
            namePaths.add(folderName);
            codePaths.add(folderCode);
            String childFolderCodePath = StringUtils.join(codePaths, ApiDocConstants.FOLDER_PATH_SEPARATOR);
            String childFolderNamePath = StringUtils.join(namePaths, ApiDocConstants.FOLDER_PATH_SEPARATOR);
            ExportApiFolderVo childFolder = folderMap.get(childFolderCodePath);
            if (childFolder == null && !StringUtils.equals(childFolderCodePath, childFolderNamePath)) {
                childFolder = folderMap.get(childFolderNamePath);
            }
            if (childFolder == null) {
                childFolder = new ExportApiFolderVo();
                childFolder.setFolderPath(childFolderCodePath);
                childFolder.setFolderCode(folderCode);
                childFolder.setFolderName(folderName);
                childFolder.setStatus(ApiDocConstants.STATUS_ENABLED);
                folderMap.put(childFolderCodePath, childFolder);
                folderMap.put(childFolderNamePath, childFolder);
            } else if (folderCodes != null) {
                if (i < folderCodes.length && StringUtils.isNotBlank(folderCodes[i])) {
                    childFolder.setFolderCode(folderCodes[i]);
                }
                if (StringUtils.isNotBlank(folderName)) {
                    childFolder.setFolderName(folderName);
                }
                childFolder.setFolderPath(childFolderCodePath);
                folderMap.put(childFolderCodePath, childFolder);
            }
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
        if (apiDocSchema != null && existsApiDocSchema != null) {
            if (StringUtils.isBlank(apiDocSchema.getExamples()) && StringUtils.isNotBlank(existsApiDocSchema.getExamples())) {
                apiDocSchema.setExamples(existsApiDocSchema.getExamples());
            }
        }
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
            projectInfoDetailVo.setVersion(Objects.requireNonNullElse(existsInfoDetail.getVersion(), 1));
            projectInfoDetailVo.setId(existsInfoDetail.getId());
            boolean isSameInfoDetail = SimpleModelUtils.isSameData(projectInfoDetailVo, existsInfoDetail, "schemaContent")
                    && ApiSchemaContentUtils.isSameSchemaContent(projectInfoDetailVo.getSchemaContent(), existsInfoDetail.getSchemaContent());
            if (ApiDocConstants.PROJECT_SCHEMA_TYPE_COMPONENT.equals(existsInfoDetail.getBodyType())) {
                if (Boolean.TRUE.equals(existsInfoDetail.getLocked())) {
                    String mergedSchemaContent = ApiSchemaContentUtils.mergeComponentSchemaContent(existsInfoDetail.getSchemaContent(),
                            projectInfoDetailVo.getSchemaContent(), isV31);
                    projectInfoDetailVo.setSchemaContent(mergedSchemaContent);
                    projectInfoDetailVo.setLocked(existsInfoDetail.getLocked());
                } else {
                    String retainedSchemaContent = ApiSchemaContentUtils.retainComponentSchemaDescription(existsInfoDetail.getSchemaContent(),
                            projectInfoDetailVo.getSchemaContent(), isV31);
                    projectInfoDetailVo.setSchemaContent(retainedSchemaContent);
                }
                if (StringUtils.isNotBlank(existsInfoDetail.getDescription())) {
                    if (StringUtils.isBlank(projectInfoDetailVo.getDescription()) ||
                            existsInfoDetail.getDescription().length() > projectInfoDetailVo.getDescription().length()) {
                        projectInfoDetailVo.setDescription(existsInfoDetail.getDescription());
                    }
                }
            } else if (ApiDocConstants.PROJECT_SCHEMA_TYPE_SECURITY.equals(existsInfoDetail.getBodyType())) {
                // 导入文档时保留已有的 x-default-auth 认证默认值，避免被新导入的数据覆盖清除
                String mergedSchemaContent = ApiSchemaContentUtils.mergeSecuritySchemaContent(existsInfoDetail.getSchemaContent(),
                        projectInfoDetailVo.getSchemaContent());
                projectInfoDetailVo.setSchemaContent(mergedSchemaContent);
            }
            // 合并/保留操作仅影响 schemaContent，统一重新比较判断是否真正有变化，避免产生不必要的历史记录
            if (!isSameInfoDetail) {
                isSameInfoDetail = ApiSchemaContentUtils.isSameSchemaContent(projectInfoDetailVo.getSchemaContent(), existsInfoDetail.getSchemaContent());
            }
            if (!isSameInfoDetail) {
                SimpleModelUtils.mergeAuditInfo(projectInfoDetailVo, existsInfoDetail);
            }
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
                int index = indexOfEnv(results, envConfig);
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
            int index = indexOfEnv(savedEnvConfigs, envConfig);
            if (index > -1) {
                SimpleModelUtils.copyNoneNullValue(savedEnvConfigs.get(index), envConfig);
            }
        }
        savedEnvConfigs.removeIf(savedConfig -> indexOfEnv(envConfigs, savedConfig) > -1);
        savedEnvConfigs.addAll(0, envConfigs);
        return savedEnvConfigs;
    }

    private static int indexOfEnv(List<ExportEnvConfigVo> list, ExportEnvConfigVo target) {
        for (int i = 0; i < list.size(); i++) {
            if (isSameEnv(list.get(i), target)) {
                return i;
            }
        }
        return -1;
    }

    public static boolean isSameEnv(ExportEnvConfigVo env1, ExportEnvConfigVo env2) {
        if (env1 == null || env2 == null) {
            return false;
        }
        return (StringUtils.isNotBlank(env1.getUrl()) && StringUtils.equals(env1.getUrl(), env2.getUrl()))
                || (StringUtils.isNotBlank(env1.getName()) && StringUtils.equals(env1.getName(), env2.getName()));
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
     * 根据 HTTP Method 与 URL 自动生成语义化的 operationId (小驼峰)
     * 例如:
     *   GET /users -> getUsers
     *   GET /users/{id} -> getUsersById
     *   POST /users -> postUsers
     *   DELETE /users/{userId}/orders/{orderId} -> deleteUsersOrdersByUserIdAndOrderId
     *   GET /api/v1/user/info -> getApiV1UserInfo
     *
     * @param method HTTP 请求方法
     * @param url 请求路径
     * @return 语义化 operationId
     */
    public static String generateOperationId(String method, String url) {
        if (StringUtils.isBlank(url)) {
            return SimpleModelUtils.uuid();
        }
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(method)) {
            sb.append(method.trim().toLowerCase());
        } else {
            sb.append("api");
        }

        // 去除 query 参数与 hash
        String cleanPath = url.split("[?#]")[0].trim();
        String[] segments = cleanPath.split("/+");

        List<String> pathParts = new ArrayList<>();
        List<String> paramParts = new ArrayList<>();

        for (String segment : segments) {
            if (StringUtils.isBlank(segment)) {
                continue;
            }
            // 路径参数识别：{id}, {userId}, :id 等
            if ((segment.startsWith("{") && segment.endsWith("}")) || segment.startsWith(":")) {
                String paramName = segment.replaceAll("[{}:]", "").trim();
                if (StringUtils.isNotBlank(paramName)) {
                    paramParts.add("By" + StringUtils.capitalize(toCamelCase(paramName)));
                }
            } else {
                String camelSegment = toCamelCase(segment);
                if (StringUtils.isNotBlank(camelSegment)) {
                    pathParts.add(StringUtils.capitalize(camelSegment));
                }
            }
        }

        for (String part : pathParts) {
            sb.append(part);
        }
        for (String param : paramParts) {
            if (paramParts.size() > 1 && param.startsWith("By") && sb.toString().contains("By")) {
                sb.append("And").append(param.substring(2));
            } else {
                sb.append(param);
            }
        }

        String result = sb.toString();
        // 过滤掉非合法标识符字符（只保留字母、数字与下划线）
        result = result.replaceAll("[^a-zA-Z0-9_]", "");
        if (StringUtils.isBlank(result) || result.equalsIgnoreCase(method)) {
            return SimpleModelUtils.uuid();
        }
        return result;
    }

    /**
     * 将带有中划线、下划线或空格的字符串转为驼峰命名
     * 例如:
     *   user-info -> userInfo
     *   order_detail -> orderDetail
     *   user_id -> userId
     *
     * @param text 待转换文本
     * @return 驼峰文本
     */
    public static String toCamelCase(String text) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        String[] words = text.split("[-_\\s]+");
        if (words.length == 1) {
            return StringUtils.uncapitalize(words[0]);
        }
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (StringUtils.isNotBlank(word)) {
                if (sb.length() == 0) {
                    sb.append(StringUtils.uncapitalize(word));
                } else {
                    sb.append(StringUtils.capitalize(word));
                }
            }
        }
        return sb.toString();
    }

    /**
     * 计算新的docKey
     * @param newDoc
     * @param apiFolder
     */
    public static void calcNewDocKey(ApiDoc newDoc, ApiFolder apiFolder) {
        if (ApiDocConstants.DOC_TYPE_API.equals(newDoc.getDocType())) {
            newDoc.setOperationId(StringUtils.defaultIfBlank(newDoc.getOperationId(), generateOperationId(newDoc.getMethod(), newDoc.getUrl())));
            String docKey = ApiDocParseUtils.calcApiDocKey(newDoc.getOperationId(), newDoc.getUrl(), newDoc.getMethod());
            if (apiFolder != null) {
                String folderIdentifier = StringUtils.defaultIfBlank(apiFolder.getFolderCode(), apiFolder.getFolderName());
                if (StringUtils.isNotBlank(folderIdentifier)) {
                    docKey = folderIdentifier + "#" + docKey;
                }
            }
            newDoc.setDocKey(docKey);
        } else if (StringUtils.isBlank(newDoc.getDocKey())) {
            newDoc.setDocKey(SimpleModelUtils.uuid());
        }
    }
}
