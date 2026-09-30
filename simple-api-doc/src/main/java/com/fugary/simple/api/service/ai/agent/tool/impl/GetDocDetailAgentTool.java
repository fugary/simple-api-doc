package com.fugary.simple.api.service.ai.agent.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.enums.ApiGroupAuthority;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectInfo;
import com.fugary.simple.api.entity.api.ApiProjectShare;
import com.fugary.simple.api.exports.ApiDocViewGenerator;
import com.fugary.simple.api.exports.md.MdViewContext;
import com.fugary.simple.api.service.ai.agent.tool.AgentContext;
import com.fugary.simple.api.service.ai.agent.tool.AgentTool;
import com.fugary.simple.api.service.apidoc.*;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectInfoDetailVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 接口/文档详情阅读工具
 */
@Slf4j
@Component
public class GetDocDetailAgentTool implements AgentTool {

    private static final int MAX_CONTENT_LENGTH = 3500;

    @Autowired
    private ApiDocService apiDocService;

    @Autowired
    private ApiProjectInfoDetailService apiDocSchemaService;

    @Autowired
    private ApiProjectInfoService apiProjectInfoService;

    @Autowired
    private ApiProjectService apiProjectService;

    @Autowired
    private ApiProjectAccessService apiProjectAccessService;

    @Autowired
    private ApiDocViewGenerator apiDocViewGenerator;

    @Override
    public String getName() {
        return "get_doc";
    }

    @Override
    public String getDescription() {
        return "阅读指定接口或Markdown文档的详细参数、说明与返回结构。当需要梳理调用步骤或说明必填项时调用。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> docIdProp = new LinkedHashMap<>();
        docIdProp.put("type", "integer");
        docIdProp.put("description", "接口或Markdown文档的ID（从 search_docs 结果中获取）");
        properties.put("docId", docIdProp);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", properties);
        params.put("required", List.of("docId"));
        return params;
    }

    @Override
    public String execute(String argumentsJson, AgentContext context) {
        try {
            JsonNode argsNode = JsonUtils.getMapper().readTree(argumentsJson);
            int docId = argsNode.path("docId").asInt(0);
            if (docId <= 0) {
                return "无效的 docId";
            }

            ApiDoc apiDoc = apiDocService.getById(docId);
            if (apiDoc == null) {
                return "未找到 ID 为 " + docId + " 的文档。";
            }

            // 权限校验
            ApiProjectShare share = context.getShare();
            if (share != null) {
                if (!Objects.equals(apiDoc.getProjectId(), share.getProjectId())) {
                    return "无权访问此文档。";
                }
                Set<Integer> shareDocIds = SimpleModelUtils.getShareDocIds(share.getShareDocs());
                if (!shareDocIds.isEmpty() && !shareDocIds.contains(docId)) {
                    return "当前分享链接未授权查看此文档。";
                }
            } else {
                if (!apiProjectAccessService.canAccessDoc(apiDoc, ApiGroupAuthority.READABLE)) {
                    return "无权访问此文档。";
                }
            }

            context.getInspectedDocIds().add(docId);

            if (ApiDocConstants.DOC_TYPE_MD.equals(apiDoc.getDocType())) {
                String content = StringUtils.trimToEmpty(apiDoc.getDocContent());
                if (content.length() > MAX_CONTENT_LENGTH) {
                    content = content.substring(0, MAX_CONTENT_LENGTH) + "\n\n...(内容过长已截断)...";
                }
                return "【Markdown文档】: " + apiDoc.getDocName() + " (ID: " + docId + ")\n" + content;
            }

            // API 接口类型生成精简的 Markdown 规格
            ApiDocDetailVo apiDocVo = apiDocSchemaService.loadDetailVo(apiDoc);
            ApiProjectInfo apiInfo = apiProjectInfoService.getById(apiDocVo.getInfoId());
            if (apiInfo != null) {
                ApiProjectInfoDetailVo apiInfoDetailVo = apiDocSchemaService.parseInfoDetailVo(apiInfo, apiDocVo);
                ApiProject apiProject = apiProjectService.getById(apiDocVo.getProjectId());
                if (apiProject != null) {
                    apiInfoDetailVo.setProjectCode(apiProject.getProjectCode());
                    apiDocVo.setProject(apiProject);
                }
                apiDocVo.setProjectInfoDetail(apiInfoDetailVo);
            }

            String markdown = apiDocViewGenerator.generate(new MdViewContext(apiDocVo));
            if (markdown != null && markdown.length() > MAX_CONTENT_LENGTH) {
                markdown = markdown.substring(0, MAX_CONTENT_LENGTH) + "\n\n...(接口字段较多已省略部分)...";
            }
            return "【接口详情】: " + apiDoc.getDocName() + " (ID: " + docId + ")\n" + markdown;
        } catch (Exception e) {
            log.error("GetDocDetailAgentTool execute error, args: {}", argumentsJson, e);
            return "获取文档详情失败: " + e.getMessage();
        }
    }
}
