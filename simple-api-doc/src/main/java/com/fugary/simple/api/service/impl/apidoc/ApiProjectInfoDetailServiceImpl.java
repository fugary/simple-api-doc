package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectInfo;
import com.fugary.simple.api.entity.api.ApiProjectInfoDetail;
import com.fugary.simple.api.mapper.api.ApiProjectInfoDetailMapper;
import com.fugary.simple.api.service.apidoc.ApiProjectInfoDetailService;
import com.fugary.simple.api.utils.SchemaJsonUtils;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.exports.ApiDocParseUtils;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectInfoDetailVo;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectInfoDetailVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Create date 2024/9/26<br>
 *
 * @author gary.fu
 */
@Slf4j
@Service
public class ApiProjectInfoDetailServiceImpl extends ServiceImpl<ApiProjectInfoDetailMapper, ApiProjectInfoDetail> implements ApiProjectInfoDetailService {

    private static final Pattern SCHEMA_COMPONENT_PATTERN = Pattern.compile("\"(" + ApiDocConstants.SCHEMA_COMPONENT_PREFIX + "[^\"]+)\"");

    @Override
    public List<ApiProjectInfoDetail> loadByProject(Integer projectId, Set<String> types) {
        return this.list(Wrappers.<ApiProjectInfoDetail>query().eq("project_id", projectId).in(!types.isEmpty(), "body_type", types));
    }

    @Override
    public List<ApiProjectInfoDetail> loadByProjectAndInfo(Integer projectId, Integer infoId, Set<String> types) {
        return this.list(Wrappers.<ApiProjectInfoDetail>query().eq("project_id", projectId)
                .eq("info_id", infoId).in(!types.isEmpty(), "body_type", types));
    }

    @Override
    public boolean deleteByProject(Integer projectId) {
        return this.remove(Wrappers.<ApiProjectInfoDetail>query().eq("project_id", projectId));
    }

    @Override
    public boolean deleteByProjectInfo(Integer projectId, Integer infoId) {
        return this.remove(Wrappers.<ApiProjectInfoDetail>query().eq("project_id", projectId)
                .eq("info_id", infoId));
    }

    @Override
    public void saveApiProjectInfoDetails(ApiProject apiProject, ApiProjectInfo apiProjectInfo, List<ExportApiProjectInfoDetailVo> projectInfoDetails) {
        Set<String> types = new HashSet<>(ApiDocConstants.PROJECT_SCHEMA_TYPES);
        types.add(ApiDocConstants.PROJECT_SCHEMA_TYPE_CONTENT);
        List<ApiProjectInfoDetail> infoDetails = loadByProjectAndInfo(apiProject.getId(), apiProjectInfo.getId(), types);
        Map<String, ApiProjectInfoDetail> detailsMap = infoDetails.stream().collect(Collectors.toMap(ApiDocParseUtils::getProjectInfoDetailKey, Function.identity()));
        deleteByProjectInfo(apiProject.getId(), apiProjectInfo.getId());
        projectInfoDetails.forEach(projectInfoDetailVo -> {
            projectInfoDetailVo.setProjectId(apiProject.getId());
            projectInfoDetailVo.setInfoId(apiProjectInfo.getId());
            ApiDocParseUtils.processProjectInfoDetail(detailsMap, projectInfoDetailVo, SchemaJsonUtils.isV31(apiProjectInfo.getSpecVersion()));
            save(SimpleModelUtils.addAuditInfo(projectInfoDetailVo));
        });
    }

    @Override
    public ApiProjectInfoDetailVo parseInfoDetailVo(ApiProjectInfo apiInfo, ApiDocDetailVo apiDocDetail) {
        List<ApiProjectInfoDetail> apiInfoDetails = loadByProjectAndInfo(apiInfo.getProjectId(), apiInfo.getId(), ApiDocConstants.PROJECT_SCHEMA_TYPES);
        ApiProjectInfoDetailVo projectInfoDetailVo = parseInfoDetailVo(apiInfo, apiInfoDetails, List.of(apiDocDetail));
        ApiDocParseUtils.overrideApiDocModifyInfo(projectInfoDetailVo, apiDocDetail);
        return projectInfoDetailVo;
    }

    @Override
    public ApiProjectInfoDetailVo mergeInfoDetailVo(List<ApiProjectInfoDetailVo> detailVoList) {
        ApiProjectInfoDetailVo result = null;
        for (ApiProjectInfoDetailVo projectInfoDetailVo : detailVoList) {
            if (result == null) {
                result = projectInfoDetailVo;
            } else {
                mergeInfoDetailVo(result, projectInfoDetailVo);
            }
        }
        return result;
    }

    protected void mergeInfoDetailVo(ApiProjectInfoDetailVo infoDetailVo, ApiProjectInfoDetailVo appendInfoDetailVo) {
        Map<String, ApiProjectInfoDetail> componentsMap = infoDetailVo.getComponentSchemas().stream().collect(Collectors.toMap(ApiProjectInfoDetail::getSchemaName, Function.identity()));
        for (ApiProjectInfoDetail componentSchemaDetail : appendInfoDetailVo.getComponentSchemas()) {
            if (!componentsMap.containsKey(componentSchemaDetail.getSchemaName())) {
                componentsMap.put(componentSchemaDetail.getSchemaName(), componentSchemaDetail);
                infoDetailVo.getComponentSchemas().add(componentSchemaDetail);
            }
        }
    }

    @Override
    public ApiProjectInfoDetailVo parseInfoDetailVo(ApiProjectInfo apiInfo, List<ApiProjectInfoDetail> apiInfoDetails, List<ApiDocDetailVo> docDetailList) {
        ApiProjectInfoDetailVo apiInfoVo = SimpleModelUtils.copy(apiInfo, ApiProjectInfoDetailVo.class);
        apiInfoDetails = filterProjectInfoDetails(apiInfo, apiInfoDetails);
        Map<String, ApiProjectInfoDetail> schemaKeyMap = toSchemaKeyMap(apiInfoDetails);
        apiInfoDetails = filterByDocDetail(apiInfoDetails, schemaKeyMap, docDetailList);
        apiInfoDetails.forEach(detail -> {
            switch (detail.getBodyType()) {
                case ApiDocConstants.PROJECT_SCHEMA_TYPE_COMPONENT:
                    apiInfoVo.getComponentSchemas().add(detail);
                    break;
                case ApiDocConstants.PROJECT_SCHEMA_TYPE_SECURITY:
                    apiInfoVo.getSecuritySchemas().add(detail);
                    break;
                case ApiDocConstants.SCHEMA_TYPE_SECURITY_REQUIREMENT:
                    apiInfoVo.getSecurityRequirements().add(detail);
                    break;
            }
        });
        if (StringUtils.isNotBlank(apiInfoVo.getAuthContent())) {
            apiInfoVo.setAuthContent(ApiDocConstants.SECURITY_CONFUSION_VALUE);
        }
        return apiInfoVo;
    }

    @Override
    public Map<String, ApiProjectInfoDetail> toSchemaKeyMap(List<ApiProjectInfoDetail> projectInfoDetails) {
        return projectInfoDetails.stream()
                .filter(detail -> StringUtils.isNotBlank(detail.getSchemaName()))
                .collect(Collectors.toMap(detail -> {
                    String schemaKey = ApiDocConstants.SCHEMA_COMPONENT_PREFIX + detail.getSchemaName();
                    detail.setSchemaKey(schemaKey);
                    return schemaKey;
                }, Function.identity()));
    }

    @Override
    public List<ApiProjectInfoDetail> filterByDocDetail(List<ApiProjectInfoDetail> projectInfoDetails, Map<String, ApiProjectInfoDetail> schemaKeyMap, List<ApiDocDetailVo> docDetailList) {
        Set<String> schemaKeys = new HashSet<>();
        for (ApiDocDetailVo docDetailVo : docDetailList) {
            if (docDetailVo.getParametersSchema() != null) {
                calcRelatedSchemas(schemaKeys, docDetailVo.getParametersSchema().getSchemaContent(), schemaKeyMap);
            }
            docDetailVo.getRequestsSchemas().forEach(schema -> calcRelatedSchemas(schemaKeys, schema.getSchemaContent(), schemaKeyMap));
            docDetailVo.getResponsesSchemas().forEach(schema -> calcRelatedSchemas(schemaKeys, schema.getSchemaContent(), schemaKeyMap));
        }
        return filterBySchemaKeys(projectInfoDetails, schemaKeys);
    }

    protected List<ApiProjectInfoDetail> filterBySchemaKeys(List<ApiProjectInfoDetail> projectInfoDetails, Set<String> schemaKeys) {
        projectInfoDetails = projectInfoDetails.stream().filter(detail -> {
            if (ApiDocConstants.PROJECT_SCHEMA_TYPE_COMPONENT.equals(detail.getBodyType())) {
                return StringUtils.isNotBlank(detail.getSchemaKey()) && schemaKeys.contains(detail.getSchemaKey());
            }
            return true;
        }).collect(Collectors.toList());
        return projectInfoDetails;
    }

    @Override
    public List<ApiProjectInfoDetail> filterByInfoDetail(List<ApiProjectInfoDetail> projectInfoDetails, Map<String, ApiProjectInfoDetail> schemaKeyMap, ApiProjectInfoDetail infoDetail) {
        Set<String> schemaKeys = new HashSet<>();
        calcRelatedSchemas(schemaKeys, infoDetail.getSchemaContent(), schemaKeyMap);
        return filterBySchemaKeys(projectInfoDetails, schemaKeys);
    }

    @Override
    public List<ApiProjectInfoDetail> findRelatedInfoDetails(ApiProjectInfoDetail infoDetail) {
        List<ApiProjectInfoDetail> infoDetails = loadByProjectAndInfo(infoDetail.getProjectId(), infoDetail.getInfoId(),
                Set.of(ApiDocConstants.PROJECT_SCHEMA_TYPE_COMPONENT));
        Map<String, ApiProjectInfoDetail> schemaKeyMap = toSchemaKeyMap(infoDetails);
        return filterByInfoDetail(infoDetails, schemaKeyMap, infoDetail);
    }

    /**
     * 计算Schema列表数据
     *
     * @param schemaKeys
     * @param content
     * @param detailMap
     */
    protected void calcRelatedSchemas(Set<String> schemaKeys, String content, Map<String, ApiProjectInfoDetail> detailMap) {
        if (StringUtils.isNotBlank(content)) {
            Matcher matcher = SCHEMA_COMPONENT_PATTERN.matcher(content);
            while (matcher.find()) {
                String schemaName = matcher.group(1);
                // 如果 schema 已经被处理过，则跳过该 schema，避免重复递归
                if (!schemaKeys.contains(schemaName)) {
                    schemaKeys.add(schemaName);  // 处理当前 schema，避免重复
                    ApiProjectInfoDetail targetDetail = detailMap.get(schemaName);
                    if (targetDetail != null && StringUtils.isNotBlank(targetDetail.getSchemaContent())) {
                        calcRelatedSchemas(schemaKeys, targetDetail.getSchemaContent(), detailMap);
                    }
                }
            }
        }
    }

    protected List<ApiProjectInfoDetail> filterProjectInfoDetails(ApiProjectInfo apiInfo, List<ApiProjectInfoDetail> apiInfoDetails) {
        if (apiInfo.getId() != null) {
            apiInfoDetails = apiInfoDetails.stream().filter(detail -> apiInfo.getId().equals(detail.getInfoId())).collect(Collectors.toList());
        }
        return apiInfoDetails;
    }
}
