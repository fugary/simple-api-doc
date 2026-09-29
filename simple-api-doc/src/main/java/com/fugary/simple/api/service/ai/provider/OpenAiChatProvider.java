package com.fugary.simple.api.service.ai.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fugary.simple.api.entity.api.AiConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import com.fugary.simple.api.service.ai.agent.tool.AiToolCall;
import com.fugary.simple.api.service.ai.agent.tool.AiToolDefinition;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class OpenAiChatProvider extends AbstractAiChatProvider {

    @Override
    public String getProviderCode() {
        return "OPENAI";
    }

    @Override
    public AiChatResponse chat(AiConfig config, AiChatRequest request) {
        HttpHeaders headers = createBearerJsonHeaders(config.getApiKey());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getDefaultModel());
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", request.getSystemPrompt()),
                Map.of("role", "user", "content", request.getUserMessage())
        ));
        if (request.getTemperature() != null) {
            requestBody.put("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            requestBody.put("max_tokens", request.getMaxTokens());
        }

        String url = buildUrl(config.getBaseUrl(), "/chat/completions");
        String rawResponse = callApi(url, headers, requestBody);
        JsonNode root = parseJson(rawResponse);
        JsonNode messageNode = root.path("choices").path(0).path("message").path("content");
        if (messageNode.isMissingNode()) {
            throw new RuntimeException("生成内容失败，AI 返回格式无法解析");
        }
        AiChatResponse chatResponse = new AiChatResponse();
        chatResponse.setRawResponse(rawResponse);
        chatResponse.setContent(cleanGeneratedContent(messageNode.asText()));
        JsonNode usageNode = root.path("usage");
        if (!usageNode.isMissingNode()) {
            chatResponse.setPromptTokens(usageNode.path("prompt_tokens").asInt());
            chatResponse.setCompletionTokens(usageNode.path("completion_tokens").asInt());
            chatResponse.setTotalTokens(usageNode.path("total_tokens").asInt());
        }
        return chatResponse;
    }

    @Override
    public AiChatResponse chatWithTools(AiConfig config, List<Map<String, Object>> messages, List<AiToolDefinition> tools, String toolChoice) {
        HttpHeaders headers = createBearerJsonHeaders(config.getApiKey());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getDefaultModel());
        requestBody.put("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            List<Map<String, Object>> formattedTools = new ArrayList<>();
            for (AiToolDefinition tool : tools) {
                Map<String, Object> fn = new HashMap<>();
                fn.put("name", tool.getName());
                fn.put("description", tool.getDescription());
                fn.put("parameters", tool.getParameters());
                Map<String, Object> toolMap = new HashMap<>();
                toolMap.put("type", "function");
                toolMap.put("function", fn);
                formattedTools.add(toolMap);
            }
            requestBody.put("tools", formattedTools);
            requestBody.put("tool_choice", StringUtils.defaultIfBlank(toolChoice, "auto"));
        }
        requestBody.put("temperature", 0.3);

        String url = buildUrl(config.getBaseUrl(), "/chat/completions");
        long start = System.currentTimeMillis();
        String rawResponse = callApi(url, headers, requestBody);
        long elapsed = System.currentTimeMillis() - start;

        JsonNode root = parseJson(rawResponse);
        JsonNode choiceNode = root.path("choices").path(0);
        JsonNode messageNode = choiceNode.path("message");
        if (messageNode.isMissingNode()) {
            throw new RuntimeException("AI 请求返回格式无法解析");
        }

        AiChatResponse chatResponse = new AiChatResponse();
        chatResponse.setRawResponse(rawResponse);
        chatResponse.setElapsedTime(elapsed);

        // 完整保留 LLM 返回的原生 message（包含 role, tool_calls, reasoning_content, thought_signature 等全部元数据）
        try {
            Map<String, Object> assistantMessage = objectMapper.convertValue(messageNode, new TypeReference<Map<String, Object>>() {});
            chatResponse.setAssistantMessage(assistantMessage);
        } catch (Exception e) {
            log.warn("转换 assistant message 结构失败", e);
        }

        JsonNode contentNode = messageNode.path("content");
        if (!contentNode.isNull() && !contentNode.isMissingNode() && StringUtils.isNotBlank(contentNode.asText())) {
            chatResponse.setContent(contentNode.asText().trim());
        }

        JsonNode toolCallsNode = messageNode.path("tool_calls");
        if (toolCallsNode.isArray() && toolCallsNode.size() > 0) {
            List<AiToolCall> toolCalls = new ArrayList<>();
            for (JsonNode tcNode : toolCallsNode) {
                String id = tcNode.path("id").asText();
                String name = tcNode.path("function").path("name").asText();
                String args = tcNode.path("function").path("arguments").asText();
                toolCalls.add(new AiToolCall(id, name, args));
            }
            chatResponse.setToolCalls(toolCalls);
        }

        JsonNode usageNode = root.path("usage");
        if (!usageNode.isMissingNode()) {
            chatResponse.setPromptTokens(usageNode.path("prompt_tokens").asInt());
            chatResponse.setCompletionTokens(usageNode.path("completion_tokens").asInt());
            chatResponse.setTotalTokens(usageNode.path("total_tokens").asInt());
        }
        return chatResponse;
    }

    @Override
    public List<String> loadModels(AiConfig config) {
        HttpHeaders headers = createBearerJsonHeaders(config.getApiKey());
        String url = buildUrl(config.getBaseUrl(), "/models");
        String rawResponse = callApiGet(url, headers);
        return extractModelIdsFromData(rawResponse);
    }
}
