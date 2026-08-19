package com.fugary.simple.api.service.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fugary.simple.api.config.AiConfigProperties;
import com.fugary.simple.api.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public abstract class AbstractAiChatProvider implements AiChatProvider {

    @Autowired
    protected AiConfigProperties aiConfigProperties;

    @Autowired
    protected ObjectMapper objectMapper;

    private static final Pattern MARKDOWN_JSON_PATTERN = Pattern.compile("```json\\s*([\\s\\S]*?)\\s*```");
    private static final Pattern MARKDOWN_GENERIC_PATTERN = Pattern.compile("```\\s*([\\s\\S]*?)\\s*```");
    private static final List<String> ERROR_KEYS = List.of(
            "message", "msg", "error_description", "detail", "error", "errors", "description", "title"
    );

    /**
     * Execute HTTP POST and return raw response body
     */
    protected String callApi(String url, HttpHeaders headers, Object requestBody) {
        RestTemplate restTemplate = createRestTemplate();
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(requestBody, headers), String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            throw new RuntimeException("AI 接口调用失败，状态码: " + response.getStatusCode());
        } catch (RestClientResponseException e) {
            String errorMsg = extractErrorMessage(e);
            log.error("AI 接口请求异常, url: {}, error: {}", url, errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    /**
     * Execute HTTP GET and return raw response body
     */
    protected String callApiGet(String url, HttpHeaders headers) {
        RestTemplate restTemplate = createRestTemplate();
        try {
            ResponseEntity<String> response = restTemplate.exchange(url,
                    org.springframework.http.HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            throw new RuntimeException("AI 接口调用失败，状态码: " + response.getStatusCode());
        } catch (RestClientResponseException e) {
            String errorMsg = extractErrorMessage(e);
            log.error("AI 接口请求异常, url: {}, error: {}", url, errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    /**
     * 解析 JSON 字符串为 JsonNode
     */
    protected JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("解析 AI 响应 JSON 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从 RestClientResponseException 中提取清晰的错误消息
     */
    public static String extractErrorMessage(RestClientResponseException e) {
        if (e == null) {
            return "未知错误";
        }
        String body = e.getResponseBodyAsString();
        String jsonMsg = extractJsonErrorMessage(body);
        if (StringUtils.isNotBlank(jsonMsg)) {
            return jsonMsg;
        }
        if (StringUtils.isNotBlank(body) && !isHtml(body)) {
            return StringUtils.abbreviate(body.trim(), 200);
        }
        return e.getRawStatusCode() + " " + e.getStatusText();
    }

    /**
     * 从 JSON 文本或包含 JSON 的字符串中提取通用错误消息
     */
    public static String extractJsonErrorMessage(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        text = text.trim();
        if (!text.startsWith("{") && text.contains("{") && text.contains("}")) {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                text = text.substring(start, end + 1);
            }
        }
        String msg = tryParseJsonErrorMessage(text);
        if (msg != null) {
            return msg;
        }
        // 如果包含转义引号或换行符，尝试解转义后再次解析
        if (text.contains("\\\"") || text.contains("\\n")) {
            String unescaped = text.replace("\\\"", "\"").replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t");
            msg = tryParseJsonErrorMessage(unescaped);
            if (msg != null) {
                return msg;
            }
        }
        return null;
    }

    private static String tryParseJsonErrorMessage(String json) {
        try {
            JsonNode root = JsonUtils.getMapper().readTree(json);
            return extractJsonErrorMessage(root);
        } catch (Exception ignore) {
            // Not a valid JSON or parsing failed
        }
        return null;
    }

    /**
     * 递归遍历 JsonNode 提取候选错误消息
     */
    public static String extractJsonErrorMessage(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual() && StringUtils.isNotBlank(node.asText())) {
            return node.asText().trim();
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String msg = extractJsonErrorMessage(item);
                if (StringUtils.isNotBlank(msg)) {
                    return msg;
                }
            }
            return null;
        }
        if (node.isObject()) {
            for (String key : ERROR_KEYS) {
                JsonNode child = node.get(key);
                if (child != null && !child.isNull() && !child.isMissingNode()) {
                    String msg = extractJsonErrorMessage(child);
                    if (StringUtils.isNotBlank(msg)) {
                        return msg;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 简单判断是否是 HTML 响应
     */
    private static boolean isHtml(String text) {
        if (StringUtils.isBlank(text)) {
            return false;
        }
        String lower = text.trim().toLowerCase();
        return lower.startsWith("<!doctype") || lower.startsWith("<html") || lower.contains("</html>");
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(aiConfigProperties.getTimeout());
        factory.setReadTimeout(aiConfigProperties.getTimeout());
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(factory);
        return restTemplate;
    }

    /**
     * Parse and extract sorted model ids from standard {"data": [{"id": "..."}, ...]} JSON response
     */
    protected List<String> extractModelIdsFromData(String rawResponse) {
        JsonNode root = parseJson(rawResponse);
        JsonNode dataNode = root.path("data");
        List<String> models = new ArrayList<>();
        if (dataNode.isArray()) {
            for (JsonNode item : dataNode) {
                String id = item.path("id").asText();
                if (id != null && !id.isBlank()) {
                    models.add(id);
                }
            }
        }
        Collections.sort(models);
        return models;
    }

    /**
     * Clean generated content by removing markdown code block syntax
     */
    protected String cleanGeneratedContent(String content) {
        if (StringUtils.isBlank(content)) {
            return "{}";
        }
        content = content.trim();
        Matcher jsonMatcher = MARKDOWN_JSON_PATTERN.matcher(content);
        if (jsonMatcher.find()) {
            return jsonMatcher.group(1).trim();
        }
        Matcher genericMatcher = MARKDOWN_GENERIC_PATTERN.matcher(content);
        if (genericMatcher.find()) {
            return genericMatcher.group(1).trim();
        }
        return content;
    }
}

