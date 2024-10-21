package com.fugary.simple.api.service.apidoc.content;

import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
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
    public SimpleResult<String> getContent(InputStream stream) {
        String result = StringUtils.EMPTY;
        if (stream != null) {
            try {
                result = StreamUtils.copyToString(stream, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("读取文件失败", e);
                return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2003);
            }
        }
        return SimpleResultUtils.createSimpleResult(result);
    }
}
