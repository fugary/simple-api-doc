package com.fugary.simple.api.service.apidoc.content;

import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.utils.http.SimpleHttpClientUtils;
import com.fugary.simple.api.web.vo.imports.BasicAuthVo;
import com.fugary.simple.api.web.vo.imports.UrlWithAuthVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

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
    public String getContent(UrlWithAuthVo source) {
        return SimpleHttpClientUtils.sendHttpGet(source.getUrl(), String.class, (client, request) -> {
           log.info("client = {}, request = {}", client, request);
            Function<UrlWithAuthVo, String> authTokenFunc = authConfigMap.get(StringUtils.lowerCase(source.getAuthType()));
            if (authTokenFunc != null) {
                request.setHeader("Authorization", authTokenFunc.apply(source));
            }
        });
    }

    protected String getBasicHeader(UrlWithAuthVo source) {
        BasicAuthVo basicAuth = JsonUtils.fromJson(source.getAuthContent(), BasicAuthVo.class);
        if (basicAuth != null) {
            return String.format(ApiDocConstants.AUTH_TYPE_BASIC + " %s", Base64.getEncoder().encodeToString((basicAuth.getUserName() + ":" + basicAuth.getUserPassword()).getBytes()));
        }
        return null;
    }
}
