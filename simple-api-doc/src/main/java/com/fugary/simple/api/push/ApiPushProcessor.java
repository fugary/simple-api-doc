package com.fugary.simple.api.push;

import com.fugary.simple.api.web.vo.query.ApiParamsVo;
import org.springframework.http.ResponseEntity;

/**
 * Create date 2024/7/17<br>
 *
 * @author gary.fu
 */
public interface ApiPushProcessor {

    /**
     * 推送处理器
     *
     * @param mockParams 请求参数
     * @return
     */
    ResponseEntity<byte[]> doPush(ApiParamsVo mockParams);
}
