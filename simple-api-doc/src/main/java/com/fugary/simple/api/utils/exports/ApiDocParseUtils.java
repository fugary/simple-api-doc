package com.fugary.simple.api.utils.exports;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiDocSchema;
import com.fugary.simple.api.entity.api.ApiProjectInfoDetail;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.web.vo.exports.*;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectInfoDetailVo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Create date 2024/9/27<br>
 *
 * @author gary.fu
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiDocParseUtils {
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
            ApiDocSchema existsSecurityRequirements = existsDocDetail.getSecurityRequirements();
            if (mergeApiDocSchema(securityRequirements, existsSecurityRequirements)) {
                isChanged = true;
            }
            ExportApiDocSchemaVo parametersSchema = apiDocVo.getParametersSchema();
            ApiDocSchema existsParametersSchema = existsDocDetail.getParametersSchema();
            if (mergeApiDocSchema(parametersSchema, existsParametersSchema)) {
                isChanged = true;
            }
            List<ExportApiDocSchemaVo> requestsSchemas = apiDocVo.getRequestsSchemas();
            Map<String, ApiDocSchema> requestSchemaMap = toSchemaMap(existsDocDetail.getRequestsSchemas());
            for (ExportApiDocSchemaVo requestsSchema : requestsSchemas) {
                ApiDocSchema existsRequestSchema = requestSchemaMap.get(getApiDocSchemaKey(requestsSchema));
                if (mergeApiDocSchema(requestsSchema, existsRequestSchema)) {
                    isChanged = true;
                }
            }
            List<ExportApiDocSchemaVo> responsesSchemas = apiDocVo.getResponsesSchemas();
            Map<String, ApiDocSchema> responseSchemaMap = toSchemaMap(existsDocDetail.getResponsesSchemas());
            for (ExportApiDocSchemaVo responsesSchema : responsesSchemas) {
                ApiDocSchema existsResponseSchema = responseSchemaMap.get(getApiDocSchemaKey(responsesSchema));
                if (mergeApiDocSchema(responsesSchema, existsResponseSchema)) {
                    isChanged = true;
                }
            }
        }
        return isChanged;
    }

    private static Map<String, ApiDocSchema> toSchemaMap(List<ApiDocSchema> list) {
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyMap();
        }
        return list.stream().collect(Collectors.toMap(ApiDocParseUtils::getApiDocSchemaKey, Function.identity()));
    }

    private static boolean mergeApiDocSchema(ExportApiDocSchemaVo apiDocSchema, ApiDocSchema existsApiDocSchema) {
        boolean isChanged = isApiDocSchemaChanged(apiDocSchema, existsApiDocSchema);
        if (existsApiDocSchema != null) {
            if (isChanged) {
                SimpleModelUtils.mergeAuditInfo(apiDocSchema, existsApiDocSchema);
            } else {
                SimpleModelUtils.mergeCreateInfo(apiDocSchema, existsApiDocSchema);
            }
        }
        return isChanged;
    }

    public static boolean isApiDocSchemaChanged(ExportApiDocSchemaVo apiDocSchema, ApiDocSchema existsApiDocSchema) {
        if (apiDocSchema == null && existsApiDocSchema == null) {
            return false;
        }
        return apiDocSchema == null || existsApiDocSchema == null
                || !StringUtils.equals(apiDocSchema.getSchemaContent(), existsApiDocSchema.getSchemaContent())
                || !StringUtils.equals(apiDocSchema.getExamples(), existsApiDocSchema.getExamples());
    }

    public static String getApiDocSchemaKey(ApiDocSchema s){
        return String.join("|", s.getSchemaName(), s.getContentType(), s.getBodyType());
    }

    public static String getProjectInfoDetailKey(ApiProjectInfoDetail infoDetail) {
        return String.join("|", infoDetail.getBodyType(), infoDetail.getSchemaName());
    }

    public static void processProjectInfoDetail(Map<String, ApiProjectInfoDetail> detailsMap,
                                                ExportApiProjectInfoDetailVo projectInfoDetailVo) {
        ApiProjectInfoDetail existsInfoDetail = detailsMap.get(ApiDocParseUtils.getProjectInfoDetailKey(projectInfoDetailVo));
        if (existsInfoDetail != null) {
            if (!StringUtils.equals(existsInfoDetail.getSchemaContent(), projectInfoDetailVo.getSchemaContent())
                    || !StringUtils.equals(existsInfoDetail.getDescription(), projectInfoDetailVo.getDescription())) {
                SimpleModelUtils.mergeAuditInfo(projectInfoDetailVo, existsInfoDetail);
            } else {
                SimpleModelUtils.mergeCreateInfo(projectInfoDetailVo, existsInfoDetail);
            }
        }
    }

    public static void overrideApiDocModifyInfo(ApiProjectInfoDetailVo projectInfoDetailVo, ApiDoc apiDoc) {
        // 查找修改时间最近的一条数据，
        Stream.of(projectInfoDetailVo.getComponentSchemas(), projectInfoDetailVo.getSecuritySchemas(),
                        projectInfoDetailVo.getSecurityRequirements()).flatMap(Collection::stream)
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
        if (envConfigs != null) {
            List<ExportEnvConfigVo> results = new ArrayList<>();
            for (ExportEnvConfigVo envConfig : envConfigs) {
                int index = SimpleModelUtils.indexOf(results, envConfig, Comparator.comparing(ExportEnvConfigVo::getUrl));
                if (index > -1) {
                    results.set(index, envConfig);
                } else {
                    results.add(envConfig);
                }
            }
        }
        return envConfigs;
    }

    public static List<ExportEnvConfigVo> mergeEnvConfigs(List<ExportEnvConfigVo> savedEnvConfigs, List<ExportEnvConfigVo> envConfigs) {
        savedEnvConfigs.removeIf(savedConfig -> !Boolean.TRUE.equals(savedConfig.getManual()));
        savedEnvConfigs.addAll(0, envConfigs);
        return savedEnvConfigs;
    }

    public static List<ExportEnvConfigVo> mergeEnvConfigs(String savedEnvConfigStr, String envConfigStr) {
        TypeReference<List<ExportEnvConfigVo>> typeReference = new TypeReference<>() {};
        List<ExportEnvConfigVo> savedEnvConfigs = StringUtils.isNotBlank(savedEnvConfigStr)? JsonUtils.fromJson(savedEnvConfigStr, typeReference): new ArrayList<>();
        List<ExportEnvConfigVo> envConfigs = StringUtils.isNotBlank(savedEnvConfigStr)? JsonUtils.fromJson(envConfigStr, typeReference): new ArrayList<>();
        return mergeEnvConfigs(savedEnvConfigs, envConfigs);
    }
}
