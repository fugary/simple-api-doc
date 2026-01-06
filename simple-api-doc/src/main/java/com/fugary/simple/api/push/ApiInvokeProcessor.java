package com.fugary.simple.api.push;

import com.fugary.simple.api.web.vo.query.ApiParamsVo;
import org.springframework.http.ResponseEntity;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Create date 2024/7/17<br>
 *
 * @author gary.fu
 */
public interface ApiInvokeProcessor {

    /**
     * 推送处理器
     *
     * @param mockParams 请求参数
     * @return
     */
    ResponseEntity<byte[]> invoke(ApiParamsVo mockParams);

    /**
     * 请求和相依发送
     *
     * @param request
     * @param response
     * @return
     */
    Object invoke(HttpServletRequest request, HttpServletResponse response);
}
