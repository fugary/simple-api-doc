package com.fugary.simple.api.service.ai.agent.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fugary.simple.api.config.AiConfigProperties;
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

    @Autowired
    private AiConfigProperties aiConfigProperties;

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
        return "分段阅读当前项目接口或Markdown文档的参数、说明与返回结构。返回 content、offset、totalLength、hasMore；"
                + "hasMore=true 时可用 nextOffset 续读。回答字段或调用步骤前须查阅详情，不能把当前段未提及当作文档不存在。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("docId", Map.of("type", "integer", "minimum", 1,
                "description", "接口或Markdown文档的ID，来自 search_docs 结果或附加的项目文档概览。"));
        properties.put("offset", Map.of("type", "integer", "minimum", 0,
                "description", "可选的正文起始位置，首次读取默认0；需要后文时原样传入上次返回的 nextOffset，不自行估算。"));
        return Map.of("type", "object", "properties", properties, "required", List.of("docId"));
    }

    @Override
    public String execute(String argumentsJson, AgentContext context) {
        try {
            JsonNode argsNode = JsonUtils.getMapper().readTree(argumentsJson);
            if (argsNode == null || !argsNode.isObject()) {
                return "请提供包含 docId 的参数对象。";
            }
            JsonNode docIdNode = argsNode.path("docId");
            if (!docIdNode.isIntegralNumber() || !docIdNode.canConvertToInt() || docIdNode.asInt() <= 0) {
                return "无效的 docId，必须为正整数。";
            }
            JsonNode offsetNode = argsNode.path("offset");
            if (!offsetNode.isMissingNode() && (!offsetNode.isIntegralNumber()
                    || !offsetNode.canConvertToInt() || offsetNode.asInt() < 0)) {
                return "无效的 offset，请使用0或上次返回的 nextOffset。";
            }
            if (context == null) {
                return "缺少文档访问上下文。";
            }
            int docId = docIdNode.asInt();
            int offset = offsetNode.asInt(0);
            ApiDoc apiDoc = apiDocService.getById(docId);
            if (apiDoc == null || apiDoc.getModifyFrom() != null
                    || (!ApiDocConstants.DOC_TYPE_API.equals(apiDoc.getDocType())
                    && !ApiDocConstants.DOC_TYPE_MD.equals(apiDoc.getDocType()))) {
                return "未找到可读取的当前版本接口或Markdown文档。";
            }
            if (context.getDefaultProjectId() != null
                    && !Objects.equals(apiDoc.getProjectId(), context.getDefaultProjectId())) {
                return "该文档不属于当前项目。";
            }
            if (!canRead(apiDoc, context.getShare())) {
                return "无权访问此文档或当前分享未授权。";
            }

            String content = loadContent(apiDoc);
            if (StringUtils.isBlank(content)) {
                return "文档暂无可读取的正文，不能据此推断接口参数或业务规则。";
            }
            if (offset >= content.length() || (offset > 0 && Character.isLowSurrogate(content.charAt(offset))
                    && Character.isHighSurrogate(content.charAt(offset - 1)))) {
                return "offset 超出正文范围或不是有效字符边界，请使用0或上次返回的 nextOffset。";
            }
            String result = buildPage(apiDoc, content, offset);
            // 成功生成并返回非空正文后才记录；已查阅某段不代表读完全文。
            context.getInspectedDocIds().add(docId);
            return result;
        } catch (Exception e) {
            log.error("GetDocDetailAgentTool execute error, args: {}", argumentsJson, e);
            return "获取文档详情失败: " + e.getMessage();
        }
    }

    private boolean canRead(ApiDoc apiDoc, ApiProjectShare share) {
        if (share == null) {
            return apiProjectAccessService.canAccessDoc(apiDoc, ApiGroupAuthority.READABLE);
        }
        if (!Objects.equals(apiDoc.getProjectId(), share.getProjectId())
                || !ApiDocConstants.STATUS_ENABLED.equals(apiDoc.getStatus())) {
            return false;
        }
        Set<Integer> shareDocIds = SimpleModelUtils.getShareDocIds(share.getShareDocs());
        if (!shareDocIds.isEmpty() && !shareDocIds.contains(apiDoc.getId())) {
            return false;
        }
        ApiProject project = apiProjectService.getById(share.getProjectId());
        return project != null && ApiDocConstants.STATUS_ENABLED.equals(project.getStatus());
    }

    private String loadContent(ApiDoc apiDoc) {
        if (ApiDocConstants.DOC_TYPE_MD.equals(apiDoc.getDocType())) {
            return StringUtils.defaultString(apiDoc.getDocContent());
        }
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
        return apiDocViewGenerator.generate(new MdViewContext(apiDocVo));
    }

    private String buildPage(ApiDoc apiDoc, String content, int offset) {
        int pageChars = Math.max(1, aiConfigProperties.getDocDetailPageChars());
        int end = offset + Math.min(pageChars, content.length() - offset);
        // UTF-16 代理对必须完整返回，避免跨段丢失 emoji 等字符；至多多返回一个字符。
        if (end < content.length() && Character.isHighSurrogate(content.charAt(end - 1))
                && Character.isLowSurrogate(content.charAt(end))) {
            end++;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("docId", apiDoc.getId());
        result.put("docName", apiDoc.getDocName());
        result.put("docType", apiDoc.getDocType());
        result.put("method", apiDoc.getMethod());
        result.put("url", apiDoc.getUrl());
        result.put("status", apiDoc.getStatus());
        result.put("deprecated", apiDoc.getDeprecated());
        result.put("offset", offset);
        result.put("totalLength", content.length());
        result.put("hasMore", end < content.length());
        if (end < content.length()) {
            result.put("nextOffset", end);
        }
        result.put("content", content.substring(offset, end));
        return JsonUtils.toJson(result);
    }
}
