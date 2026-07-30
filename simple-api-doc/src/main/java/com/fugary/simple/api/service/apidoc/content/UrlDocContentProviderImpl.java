package com.fugary.simple.api.service.apidoc.content;

import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.exception.SimpleRuntimeException;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.http.SimpleHttpClientUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.imports.BasicAuthVo;
import com.fugary.simple.api.web.vo.imports.UrlWithAuthVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.util.EntityUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.function.Function;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
@Slf4j
@Primary
@Component
public class UrlDocContentProviderImpl implements DocContentProvider<UrlWithAuthVo> {

    private final Map<String, Function<UrlWithAuthVo, String>> authConfigMap
            = Map.of(StringUtils.lowerCase(ApiDocConstants.AUTH_TYPE_BASIC), this::getBasicHeader);

    @Override
    public SimpleResult<String> getContent(UrlWithAuthVo source) {
        if (source == null || StringUtils.isBlank(source.getUrl())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2009);
        }
        Pair<String,HttpResponse> resultPair = null;
        try {
            resultPair = SimpleHttpClientUtils.sendHttpGet(source.getUrl(), Pair.class, (client, request) -> {
                log.info("client = {}, request = {}", client, request);
                Function<UrlWithAuthVo, String> authTokenFunc = authConfigMap.get(StringUtils.lowerCase(source.getAuthType()));
                if (authTokenFunc != null) {
                    request.setHeader("Authorization", authTokenFunc.apply(source));
                }
            }, (httpResponse, clazz) -> {
                String resultStr = StringUtils.EMPTY;
                try {
                    resultStr = EntityUtils.toString(httpResponse.getEntity(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    log.error("Url数据解析错误", e);
                }
                return Pair.of(resultStr, httpResponse);
            });
        } catch (SimpleRuntimeException e) {
            log.error("URL数据下载异常: url={}", source.getUrl(), e);
            String baseMsg = SimpleResultUtils.getErrorMsg(SystemErrorConstants.CODE_2009);
            String detail = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            String errorMsg = StringUtils.isNotBlank(detail) ? baseMsg + ": " + detail : baseMsg;
            return SimpleResultUtils.createError(errorMsg);
        }
        if (resultPair != null) {
            HttpResponse httpResponse = resultPair.getRight();
            if (httpResponse != null) {
                int statusCode = httpResponse.getStatusLine().getStatusCode();
                if (HttpStatus.SC_OK == statusCode) {
                    return SimpleResultUtils.createSimpleResult(resultPair.getLeft());
                }
                String statusLineStr = httpResponse.getStatusLine().toString();
                log.error("URL数据下载失败: url={}, status={}", source.getUrl(), statusLineStr);
                String baseMsg = SimpleResultUtils.getErrorMsg(SystemErrorConstants.CODE_2009);
                return SimpleResultUtils.createError(baseMsg + ": " + statusLineStr);
            }
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2005);
        }
        return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2009);
    }

    protected String getBasicHeader(UrlWithAuthVo source) {
        BasicAuthVo basicAuth = JsonUtils.fromJson(source.getAuthContent(), BasicAuthVo.class);
        if (basicAuth != null) {
            return String.format(ApiDocConstants.AUTH_TYPE_BASIC + " %s", Base64.getEncoder().encodeToString((basicAuth.getUserName() + ":" + basicAuth.getUserPassword()).getBytes()));
        }
        return null;
    }
}
