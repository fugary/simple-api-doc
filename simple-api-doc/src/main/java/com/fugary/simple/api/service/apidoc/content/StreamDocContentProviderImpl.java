package com.fugary.simple.api.service.apidoc.content;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
@Slf4j
@Component
public class StreamDocContentProviderImpl implements DocContentProvider<InputStream> {

    @Override
    public String getContent(InputStream stream) {
        if (stream != null) {
            try {
                StreamUtils.copyToString(stream, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("读取文件失败", e);
            }
        }
        return StringUtils.EMPTY;
    }
}
