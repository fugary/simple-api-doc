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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.MultiValueMapAdapter;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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

    @Override
    public ResponseEntity<?> invoke(HttpServletRequest request, HttpServletResponse response) {
        String targetUrl = request.getHeader(ApiDocConstants.SIMPLE_API_TARGET_URL_HEADER);
        if (StringUtils.isBlank(targetUrl)) {
            return ResponseEntity.ok(SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404));
        }
        ResponseEntity<byte[]> responseEntity = invoke(SimpleModelUtils.toApiParams(request));
        List<NameValue> requestHeaders = HttpRequestUtils.getRequestHeaders(request);
        response.addHeader(ApiDocConstants.SIMPLE_API_META_DATA_REQ, JsonUtils.toJson(requestHeaders));
        return responseEntity;
    }

    @Override
    public ResponseEntity<byte[]> invoke(ApiParamsVo mockParams) {
        String requestUrl = getRequestUrl(mockParams.getTargetUrl(), mockParams);
        HttpEntity<?> entity = new HttpEntity<>(mockParams.getRequestBody(), getHeaders(mockParams));
        if (HttpRequestUtils.isCompatibleWith(mockParams, MediaType.MULTIPART_FORM_DATA, MediaType.APPLICATION_FORM_URLENCODED)) {
            entity = new HttpEntity<>(getMultipartBody(mockParams), getHeaders(mockParams));
        }
        try {
            SimpleLogUtils.addRequestData(mockParams);
            ResponseEntity<byte[]> responseEntity = restTemplate.exchange(requestUrl,
                    Optional.ofNullable(HttpMethod.resolve(mockParams.getMethod())).orElse(HttpMethod.GET),
                    entity, byte[].class);
            responseEntity = processRedirect(responseEntity, mockParams, entity);
            return SimpleModelUtils.removeProxyHeaders(responseEntity);
        } catch (HttpClientErrorException e) {
            return SimpleModelUtils.removeProxyHeaders(ResponseEntity.status(e.getStatusCode())
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
                        .queryParams(getQueryParams(mockParams))
                        .build(true).toUri();
                responseEntity = restTemplate.exchange(targetUri,
                        Optional.ofNullable(HttpMethod.resolve(mockParams.getMethod())).orElse(HttpMethod.GET),
                        entity, byte[].class);
            }
        }
        return responseEntity;
    }

    protected MultiValueMap<String, Object> getMultipartBody(ApiParamsVo mockParams) {
        MultiValueMap<String, Object> bodyMap = new LinkedMultiValueMap<>();
        mockParams.getFormData().forEach(nv -> {
            if (nv.getValue() instanceof Iterable) {
                ((Iterable) nv.getValue()).forEach(v -> bodyMap.add(nv.getName(), ((MultipartFile) v).getResource()));
            } else {
                bodyMap.add(nv.getName(), nv.getValue());
            }
        });
        mockParams.getFormUrlencoded().forEach(nv -> {
            bodyMap.add(nv.getName(), nv.getValue());
        });
        return bodyMap;
    }

    /**
     * 计算请求url地址
     *
     * @param baseUrl
     * @param mockParams
     * @return
     */
    protected String getRequestUrl(String baseUrl, ApiParamsVo mockParams) {
        String requestUrl = mockParams.getRequestPath();
        requestUrl = requestUrl.replaceAll(":([\\w-]+)", "{$1}"); // spring 支持的ant path不支持:var格式，只支持{var}格式
        requestUrl = UriComponentsBuilder.fromUriString(baseUrl)
                .path(requestUrl)
                .queryParams(getQueryParams(mockParams))
                .build(true).toUriString();
        for (NameValue nv : mockParams.getPathParams()) {
            requestUrl = requestUrl.replace("{" + nv.getName() + "}", nv.getValue());
        }
        return requestUrl;
    }

    /**
     * 获取头信息
     *
     * @param paramsVo
     * @return
     */
    protected HttpHeaders getHeaders(ApiParamsVo paramsVo) {
        HttpHeaders headers = new HttpHeaders();
        paramsVo.getHeaderParams().forEach(nv -> {
            if (StringUtils.equalsIgnoreCase(nv.getName(), HttpHeaders.ACCEPT_ENCODING)
                    && StringUtils.containsIgnoreCase(nv.getValue(), "zstd")) {
                nv.setValue("gzip, deflate, br");
            }
            headers.addIfAbsent(nv.getName(), nv.getValue());
        });
        return headers;
    }

    /**
     * 获取参数
     *
     * @param paramsVo
     * @return
     */
    protected MultiValueMap<String, String> getQueryParams(ApiParamsVo paramsVo) {
        return new MultiValueMapAdapter<>(paramsVo.getRequestParams().stream()
                .collect(Collectors.toMap(NameValue::getName, nv -> List.of(nv.getValue()))));
    }
}
