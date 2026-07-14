package com.fugary.simple.api.service.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fugary.simple.api.config.AiConfigProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

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

    /**
     * Execute HTTP POST and return raw response body
     */
    protected String callApi(String url, HttpHeaders headers, Object requestBody) {
        RestTemplate restTemplate = createRestTemplate();
        ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(requestBody, headers), String.class);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return response.getBody();
        }
        throw new RuntimeException("AI 接口调用失败，状态码: " + response.getStatusCode());
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
