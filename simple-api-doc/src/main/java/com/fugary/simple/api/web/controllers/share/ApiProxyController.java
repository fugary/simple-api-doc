package com.fugary.simple.api.web.controllers.share;

import com.fugary.simple.api.push.ApiPushProcessor;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.web.vo.query.ApiParamsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RequestMapping("/api")
@RestController
public class ApiProxyController {

    @Autowired
    private ApiPushProcessor apiPushProcessor;

    /**
     * 调试API
     *
     * @return
     */
    @RequestMapping("/proxy/**")
    public ResponseEntity<?> debugApi(HttpServletRequest request, HttpServletResponse response) {
        ApiParamsVo paramsVo = new ApiParamsVo();
        SimpleModelUtils.toApiParams(paramsVo, request);
        return apiPushProcessor.doPush(paramsVo);
    }
}
