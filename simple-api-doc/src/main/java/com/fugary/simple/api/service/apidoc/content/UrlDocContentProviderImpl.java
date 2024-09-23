package com.fugary.simple.api.service.apidoc.content;

import com.fugary.simple.api.utils.http.SimpleHttpClientUtils;
import com.fugary.simple.api.web.vo.imports.UrlWithAuthVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
@Slf4j
@Primary
@Component
public class UrlDocContentProviderImpl implements DocContentProvider<UrlWithAuthVo> {

    @Override
    public String getContent(UrlWithAuthVo source) {
        return SimpleHttpClientUtils.sendHttpGet(source.getUrl(), String.class, (client, request) -> {
           log.info("client = {}, request = {}", client, request);
        });
    }
}
