package com.fugary.simple.api.service.ai;

import com.fugary.simple.api.service.ai.provider.AbstractAiChatProvider;
import com.fugary.simple.api.service.impl.ai.AiServiceImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.nio.charset.StandardCharsets;

public class AiErrorMessageExtractTest {

    @Test
    public void testExtractGemini503Error() {
        String json = "{\n" +
                "  \"error\": {\n" +
                "    \"code\": 503,\n" +
                "    \"message\": \"This model is currently experiencing high demand. Spikes in demand are usually temporary. Please try again later.\",\n" +
                "    \"status\": \"UNAVAILABLE\"\n" +
                "  }\n" +
                "}";
        String message = AbstractAiChatProvider.extractJsonErrorMessage(json);
        Assertions.assertEquals("This model is currently experiencing high demand. Spikes in demand are usually temporary. Please try again later.", message);
    }

    @Test
    public void testExtractOpenAiError() {
        String json = "{\n" +
                "  \"error\": {\n" +
                "    \"message\": \"Incorrect API key provided: sk-***\",\n" +
                "    \"type\": \"invalid_request_error\",\n" +
                "    \"param\": null,\n" +
                "    \"code\": \"invalid_api_key\"\n" +
                "  }\n" +
                "}";
        String message = AbstractAiChatProvider.extractJsonErrorMessage(json);
        Assertions.assertEquals("Incorrect API key provided: sk-***", message);
    }

    @Test
    public void testExtractAnthropicError() {
        String json = "{\n" +
                "  \"type\": \"error\",\n" +
                "  \"error\": {\n" +
                "    \"type\": \"authentication_error\",\n" +
                "    \"message\": \"invalid x-api-key\"\n" +
                "  }\n" +
                "}";
        String message = AbstractAiChatProvider.extractJsonErrorMessage(json);
        Assertions.assertEquals("invalid x-api-key", message);
    }

    @Test
    public void testExtractOllamaStringError() {
        String json = "{\n" +
                "  \"error\": \"model 'llama3' not found, try pulling it first\"\n" +
                "}";
        String message = AbstractAiChatProvider.extractJsonErrorMessage(json);
        Assertions.assertEquals("model 'llama3' not found, try pulling it first", message);
    }

    @Test
    public void testExtractFastApiDetailArrayError() {
        String json = "{\n" +
                "  \"detail\": [\n" +
                "    {\n" +
                "      \"loc\": [\"body\", \"prompt\"],\n" +
                "      \"msg\": \"field required\",\n" +
                "      \"type\": \"value_error.missing\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        String message = AbstractAiChatProvider.extractJsonErrorMessage(json);
        Assertions.assertEquals("field required", message);
    }

    @Test
    public void testExtractFastApiDetailFlatError() {
        String json = "{\n" +
                "  \"detail\": \"Not Found\"\n" +
                "}";
        String message = AbstractAiChatProvider.extractJsonErrorMessage(json);
        Assertions.assertEquals("Not Found", message);
    }

    @Test
    public void testExtractOAuthErrorDescription() {
        String json = "{\n" +
                "  \"error\": \"invalid_grant\",\n" +
                "  \"error_description\": \"The authorization code has expired\"\n" +
                "}";
        String message = AbstractAiChatProvider.extractJsonErrorMessage(json);
        Assertions.assertEquals("The authorization code has expired", message);
    }

    @Test
    public void testExtractGenericMessageError() {
        String json = "{\n" +
                "  \"code\": 401,\n" +
                "  \"message\": \"Token expired\"\n" +
                "}";
        String message = AbstractAiChatProvider.extractJsonErrorMessage(json);
        Assertions.assertEquals("Token expired", message);
    }

    @Test
    public void testExtractErrorsArray() {
        String json = "{\n" +
                "  \"errors\": [\n" +
                "    {\"message\": \"Rate limit reached\"}\n" +
                "  ]\n" +
                "}";
        String message = AbstractAiChatProvider.extractJsonErrorMessage(json);
        Assertions.assertEquals("Rate limit reached", message);
    }

    @Test
    public void testExtractFromRestClientResponseException() {
        String json = "{\n" +
                "  \"error\": {\n" +
                "    \"code\": 503,\n" +
                "    \"message\": \"This model is currently experiencing high demand. Spikes in demand are usually temporary. Please try again later.\",\n" +
                "    \"status\": \"UNAVAILABLE\"\n" +
                "  }\n" +
                "}";
        HttpServerErrorException exception = HttpServerErrorException.create(
                HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                HttpHeaders.EMPTY, json.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8
        );
        String message = AbstractAiChatProvider.extractErrorMessage(exception);
        Assertions.assertEquals("This model is currently experiencing high demand. Spikes in demand are usually temporary. Please try again later.", message);
    }

    @Test
    public void testExtractFromExceptionChain() {
        String json = "{\n" +
                "  \"error\": {\n" +
                "    \"code\": 503,\n" +
                "    \"message\": \"This model is currently experiencing high demand. Spikes in demand are usually temporary. Please try again later.\",\n" +
                "    \"status\": \"UNAVAILABLE\"\n" +
                "  }\n" +
                "}";
        HttpServerErrorException httpEx = HttpServerErrorException.create(
                HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                HttpHeaders.EMPTY, json.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8
        );
        RuntimeException runtimeEx = new RuntimeException("Gemini Chat failed", httpEx);
        String simpleMsg = AiServiceImpl.getSimpleErrorMessage(runtimeEx);
        Assertions.assertEquals("This model is currently experiencing high demand. Spikes in demand are usually temporary. Please try again later.", simpleMsg);
    }

    @Test
    public void testExtractFromEmbeddedJsonInExceptionMessage() {
        String rawMsg = "503 Service Unavailable: \"{\\n  \\\"error\\\": {\\n    \\\"code\\\": 503,\\n    \\\"message\\\": \\\"High demand\\\",\\n    \\\"status\\\": \\\"UNAVAILABLE\\\"\\n  }\\n}\"";
        RuntimeException ex = new RuntimeException(rawMsg);
        String simpleMsg = AiServiceImpl.getSimpleErrorMessage(ex);
        Assertions.assertEquals("High demand", simpleMsg);
    }

    @Test
    public void testExtractPlainTextError() {
        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request",
                HttpHeaders.EMPTY, "upstream connect error".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8
        );
        String message = AbstractAiChatProvider.extractErrorMessage(exception);
        Assertions.assertEquals("upstream connect error", message);
    }

    @Test
    public void testExtractHtmlFallback() {
        String html = "<html><head><title>502 Bad Gateway</title></head><body><h1>502 Bad Gateway</h1></body></html>";
        HttpServerErrorException exception = HttpServerErrorException.create(
                HttpStatus.BAD_GATEWAY, "Bad Gateway",
                HttpHeaders.EMPTY, html.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8
        );
        String message = AbstractAiChatProvider.extractErrorMessage(exception);
        Assertions.assertEquals("502 Bad Gateway", message);
    }

    @Test
    public void testCleanJavaExceptionPrefix() {
        RuntimeException ex = new RuntimeException("java.lang.RuntimeException: java.net.SocketTimeoutException: Read timed out");
        String simpleMsg = AiServiceImpl.getSimpleErrorMessage(ex);
        Assertions.assertEquals("Read timed out", simpleMsg);
    }
}
