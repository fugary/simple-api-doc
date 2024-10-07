package com.fugary.simple.api.web.controllers.share;

import com.fugary.simple.api.push.ApiInvokeProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RequestMapping("/admin")
@RestController
public class ApiProxyController {

    @Autowired
    private ApiInvokeProcessor apiInvokeProcessor;

    /**
     * 调试API
     *
     * @return
     */
    @RequestMapping("/proxy/**")
    public ResponseEntity<?> proxyApi(HttpServletRequest request, HttpServletResponse response) {
        return apiInvokeProcessor.invoke(request, response);
    }
}
