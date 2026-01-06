package com.fugary.simple.api.push;

import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.utils.SimpleLogUtils;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.servlet.HttpRequestUtils;
import com.fugary.simple.api.web.vo.NameValue;
import com.fugary.simple.api.web.vo.query.ApiParamsVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Create date 2024/7/17<br>
 *
 * @author gary.fu
 */
@Slf4j
@Component
public class DefaultApiInvokeProcessorImpl implements ApiInvokeProcessor {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ApiSseInvokeProcessor apiSseInvokeProcessor;

    @Override
    public Object invoke(HttpServletRequest request, HttpServletResponse response) {
        String targetUrl = request.getHeader(ApiDocConstants.SIMPLE_API_TARGET_URL_HEADER);
        if (StringUtils.isBlank(targetUrl)) {
            return ResponseEntity.ok(SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404));
        }
        String acceptHeader = request.getHeader(HttpHeaders.ACCEPT);
        if (StringUtils.contains(acceptHeader, MediaType.TEXT_EVENT_STREAM_VALUE)) {
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            return apiSseInvokeProcessor.processSseProxy(SimpleModelUtils.toApiParams(request));
        }
        ResponseEntity<byte[]> responseEntity = invoke(SimpleModelUtils.toApiParams(request));
        List<NameValue> requestHeaders = HttpRequestUtils.getRequestHeaders(request);
        response.addHeader(ApiDocConstants.SIMPLE_API_META_DATA_REQ, JsonUtils.toJson(requestHeaders));
        return responseEntity;
    }

    @Override
    public ResponseEntity<byte[]> invoke(ApiParamsVo mockParams) {
        String requestUrl = HttpRequestUtils.getRequestUrl(mockParams.getTargetUrl(), mockParams);
        HttpEntity<?> entity = new HttpEntity<>(mockParams.getRequestBody(), HttpRequestUtils.getHeaders(mockParams));
        if (HttpRequestUtils.isCompatibleWith(mockParams, MediaType.MULTIPART_FORM_DATA, MediaType.APPLICATION_FORM_URLENCODED)) {
            entity = new HttpEntity<>(HttpRequestUtils.getMultipartBody(mockParams), HttpRequestUtils.getHeaders(mockParams));
        }
        try {
            SimpleLogUtils.addRequestData(mockParams);
            ResponseEntity<byte[]> responseEntity = restTemplate.exchange(URI.create(requestUrl),
                    Optional.ofNullable(HttpMethod.resolve(mockParams.getMethod())).orElse(HttpMethod.GET),
                    entity, byte[].class);
            responseEntity = processRedirect(responseEntity, mockParams, entity);
            return SimpleModelUtils.removeProxyHeaders(responseEntity);
        } catch (RestClientResponseException e) {
            return SimpleModelUtils.removeProxyHeaders(ResponseEntity.status(e.getRawStatusCode())
                    .headers(e.getResponseHeaders())
                    .body(e.getResponseBodyAsByteArray()));
        } catch (Exception e) {
            log.error("获取数据错误", e);
        }
        return ResponseEntity.notFound().build();
    }

    protected ResponseEntity<byte[]> processRedirect(ResponseEntity<byte[]> responseEntity,
                                                   ApiParamsVo mockParams,
                                                   HttpEntity<?> entity) {
        HttpStatus httpStatus = responseEntity.getStatusCode();
        if (httpStatus.is3xxRedirection()) {
            URI location = responseEntity.getHeaders().getLocation();
            if (location != null) {
                URI targetUri = UriComponentsBuilder.fromUri(location)
                        .queryParams(HttpRequestUtils.getQueryParams(mockParams))
                        .encode()
                        .build().toUri();
                responseEntity = restTemplate.exchange(targetUri,
                        Optional.ofNullable(HttpMethod.resolve(mockParams.getMethod())).orElse(HttpMethod.GET),
                        entity, byte[].class);
            }
        }
        return responseEntity;
    }
}
