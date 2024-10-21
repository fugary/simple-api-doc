package com.fugary.simple.api.service.apidoc.content;

import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.http.SimpleHttpClientUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.imports.BasicAuthVo;
import com.fugary.simple.api.web.vo.imports.UrlWithAuthVo;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.util.EntityUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

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

    @SneakyThrows
    @Override
    public SimpleResult<String> getContent(UrlWithAuthVo source) {
        HttpResponse httpResponse = SimpleHttpClientUtils.sendHttpGet(source.getUrl(), HttpResponse.class, (client, request) -> {
            log.info("client = {}, request = {}", client, request);
            Function<UrlWithAuthVo, String> authTokenFunc = authConfigMap.get(StringUtils.lowerCase(source.getAuthType()));
            if (authTokenFunc != null) {
                request.setHeader("Authorization", authTokenFunc.apply(source));
            }
        });
        if (httpResponse != null && HttpStatus.SC_OK == httpResponse.getStatusLine().getStatusCode()) {
            return SimpleResultUtils.createSimpleResult(EntityUtils.toString(httpResponse.getEntity(), StandardCharsets.UTF_8));
        }
        return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2005);
    }

    protected String getBasicHeader(UrlWithAuthVo source) {
        BasicAuthVo basicAuth = JsonUtils.fromJson(source.getAuthContent(), BasicAuthVo.class);
        if (basicAuth != null) {
            return String.format(ApiDocConstants.AUTH_TYPE_BASIC + " %s", Base64.getEncoder().encodeToString((basicAuth.getUserName() + ":" + basicAuth.getUserPassword()).getBytes()));
        }
        return null;
    }
}
