package com.fugary.simple.api.web.controllers.share;

import com.fugary.simple.api.push.ApiPushProcessor;
import com.fugary.simple.api.web.vo.query.ApiParamsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api")
@RestController
public class ApiProxyController {

    @Autowired
    private ApiPushProcessor apiPushProcessor;

    /**
     * 调试API
     *
     * @param paramsVo
     * @return
     */
    @PostMapping("/proxy")
    public ResponseEntity<?> debugApi(@RequestBody ApiParamsVo paramsVo) {
        return apiPushProcessor.doPush(paramsVo);
    }
}
