package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectInfo;
import com.fugary.simple.api.entity.api.ApiProjectInfoDetail;
import com.fugary.simple.api.mapper.api.ApiProjectInfoDetailMapper;
import com.fugary.simple.api.service.apidoc.ApiProjectInfoDetailService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectInfoDetailVo;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Service;

import java.util.*;
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

    private final StringHttpMessageConverter stringHttpMessageConverter;

    public ApiProjectInfoDetailServiceImpl(StringHttpMessageConverter stringHttpMessageConverter) {
        this.stringHttpMessageConverter = stringHttpMessageConverter;
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
        deleteByProjectInfo(apiProject.getId(), apiProjectInfo.getId());
        projectInfoDetails.forEach(projectInfoDetailVo -> {
            projectInfoDetailVo.setProjectId(apiProject.getId());
            projectInfoDetailVo.setInfoId(apiProjectInfo.getId());
            save(SimpleModelUtils.addAuditInfo(projectInfoDetailVo));
        });
    }

    @Override
    public Map<String, ApiProjectInfoDetail> toSchemaKeyMap(List<ApiProjectInfoDetail> projectInfoDetails) {
        return projectInfoDetails.stream()
                .filter(detail -> StringUtils.isNotBlank(detail.getSchemaName()))
                .collect(Collectors.toMap(detail -> {
                    String schemaKey = "#/components/schemas/" + detail.getSchemaName();
                    detail.setSchemaKey(schemaKey);
                    return schemaKey;
                }, Function.identity()));
    }

    @Override
    public List<ApiProjectInfoDetail> filterByDocDetail(List<ApiProjectInfoDetail> projectInfoDetails, Map<String, ApiProjectInfoDetail> schemaKeyMap, ApiDocDetailVo docDetailVo) {
        Set<String> schemaKeys = new HashSet<>();
        docDetailVo.getRequestsSchemas().forEach(schema -> calcSchemaList(schemaKeys, schema.getSchemaContent(), schemaKeyMap));
        docDetailVo.getResponsesSchemas().forEach(schema -> calcSchemaList(schemaKeys, schema.getSchemaContent(), schemaKeyMap));
        projectInfoDetails = projectInfoDetails.stream()
                .filter(detail -> StringUtils.isNotBlank(detail.getSchemaKey()) && schemaKeys.contains(detail.getSchemaKey()))
                .collect(Collectors.toList());
        return projectInfoDetails;
    }

    /**
     * 计算Schema列表数据
     *
     * @param schemaKeys
     * @param content
     * @param detailMap
     */
    protected void calcSchemaList(Set<String> schemaKeys, String content, Map<String, ApiProjectInfoDetail> detailMap) {
        log.info("====content:{}", content);
        Pattern pattern = Pattern.compile("\"(#/components/schemas/[^\"]+)\"");
        Matcher matcher = pattern.matcher(content);
        String schemaName = null;
        while (matcher.find()) {
            schemaName = matcher.group(1);
            if (!schemaKeys.contains(schemaName)) {
                ApiProjectInfoDetail targetDetail = detailMap.get(schemaName);
                if (targetDetail != null) {
                    calcSchemaList(schemaKeys, targetDetail.getSchemaContent(), detailMap);
                }
            }
        }
        if (StringUtils.isNotBlank(schemaName)) {
            schemaKeys.add(schemaName);
        }
    }
}
