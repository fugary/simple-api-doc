package com.fugary.simple.api.service.ai.agent.tool.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fugary.simple.api.service.ai.agent.tool.AgentContext;
import com.fugary.simple.api.service.ai.agent.tool.AgentTool;
import com.fugary.simple.api.service.apidoc.ApiDocSearchService;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.web.vo.ai.CompactSearchDocDto;
import com.fugary.simple.api.web.vo.doc.ApiDocSearchResultVo;
import com.fugary.simple.api.web.vo.query.ApiDocSearchQueryVo;
import com.fugary.simple.api.web.vo.query.SimplePage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 文档/接口搜索工具
 */
@Slf4j
@Component
public class SearchDocsAgentTool implements AgentTool {

    @Autowired
    private ApiDocSearchService apiDocSearchService;

    @Override
    public String getName() {
        return "search_docs";
    }

    @Override
    public String getDescription() {
        return "在系统文档库中搜索相关接口或Markdown文档。支持输入业务关键词、HTTP请求方法或针对特定项目检索。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> keywordsProp = new LinkedHashMap<>();
        keywordsProp.put("type", "string");
        keywordsProp.put("description", "按字面包含匹配的关键词或路径，不自动翻译、扩展同义词或删除尾缀。空格分隔关键词；match=any 用于同义表达，match=all 用于必须同时满足的不同条件。明确路径请保留原文。");
        keywordsProp.put("maxLength", 200);
        properties.put("keywords", keywordsProp);
        properties.put("match", Map.of("type", "string", "enum", List.of("any", "all"),
                "description", "默认 any：匹配任意词；all：每个词都必须命中，可分布在名称、路径或正文。"));
        properties.put("page", Map.of("type", "integer", "minimum", 1,
                "description", "结果页码，默认 1；hasMore=true 且证据不足时可以读取下一页。"));

        Map<String, Object> methodProp = new LinkedHashMap<>();
        methodProp.put("type", "string");
        methodProp.put("description", "可选的 HTTP 请求方法，如 GET, POST, PUT, DELETE");
        properties.put("method", methodProp);

        Map<String, Object> projectIdProp = new LinkedHashMap<>();
        projectIdProp.put("type", "integer");
        projectIdProp.put("description", "可选的项目ID，指定后仅在当前项目下检索");
        properties.put("projectId", projectIdProp);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", properties);
        params.put("required", List.of("keywords"));
        return params;
    }

    @Override
    public String execute(String argumentsJson, AgentContext context) {
        try {
            JsonNode argsNode = JsonUtils.getMapper().readTree(argumentsJson);
            String keywords = argsNode.path("keywords").asText("").trim();
            if (keywords.isEmpty() || keywords.length() > 200) {
                return "请提供 1 到 200 个字符的检索关键词。";
            }
            String match = argsNode.path("match").asText("any");
            if (!List.of("any", "all").contains(match)) {
                return "match 仅支持 any 或 all。";
            }
            String method = argsNode.path("method").asText(null);
            Integer projectId = (context != null && context.getDefaultProjectId() != null)
                    ? context.getDefaultProjectId()
                    : (argsNode.hasNonNull("projectId") ? Integer.valueOf(argsNode.path("projectId").asInt()) : null);

            ApiDocSearchQueryVo query = new ApiDocSearchQueryVo();
            query.setKeyword(keywords);
            query.setKeywordMatch(match);
            if (StringUtils.isNotBlank(method)) {
                query.setMethod(method.trim().toUpperCase(Locale.ROOT));
            }
            if (projectId != null && projectId > 0) {
                query.setProjectId(projectId);
            }
            SimplePage page = new SimplePage();
            page.setPageNumber(Math.max(1, argsNode.path("page").asInt(1)));
            page.setPageSize(8); // 控制返回条数，避免 Token 膨胀
            query.setPage(page);

            Page<ApiDocSearchResultVo> searchResult = apiDocSearchService.search(query, context.getShare());
            List<ApiDocSearchResultVo> records = searchResult.getRecords();

            List<CompactSearchDocDto> compactList = records.stream().map(doc -> CompactSearchDocDto.builder()
                        .docId(doc.getId())
                        .docName(doc.getDocName())
                        .docType(doc.getDocType())
                        .method(doc.getMethod())
                        .url(doc.getUrl())
                        .description(doc.getSnippet())
                        .projectId(doc.getProjectId())
                        .projectName(doc.getProjectName())
                        .folderPath(doc.getFolderPath())
                        .build()).collect(Collectors.toList());
            compactList.forEach(doc -> context.getDiscoveredDocIds().add(doc.getDocId()));
            return JsonUtils.toJson(Map.of("total", searchResult.getTotal(),
                    "page", searchResult.getCurrent(), "hasMore", searchResult.hasNext(), "docs", compactList));
        } catch (Exception e) {
            log.error("SearchDocsAgentTool execute error, args: {}", argumentsJson, e);
            return "检索执行异常: " + e.getMessage();
        }
    }
}
