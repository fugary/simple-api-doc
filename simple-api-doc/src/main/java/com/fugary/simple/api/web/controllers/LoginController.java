package com.fugary.simple.api.web.controllers;

import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.service.apidoc.ApiUserService;
import com.fugary.simple.api.service.token.TokenService;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.LoginResultVo;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.query.LoginVo;
import com.fugary.simple.api.web.vo.user.ApiUserVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class LoginController {

    @Autowired
    private ApiUserService apiUserService;

    @Autowired
    private TokenService tokenService;

    @Value("${spring.h2.console.enabled:false}")
    private boolean consoleEnabled;

    @PostMapping("/login")
    public SimpleResult<LoginResultVo> login(@RequestBody LoginVo user) {
        ApiUserVo loginUser = apiUserService.loadUser(user.getUserName());
        if (loginUser == null || !loginUser.isEnabled() || !apiUserService.matchPassword(user.getUserPassword(), loginUser.getUserPassword())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2001);
        }
        LoginResultVo resultVo = new LoginResultVo();
        resultVo.setAccount(loginUser);
        resultVo.setAccessToken(tokenService.createToken(loginUser));
        resultVo.setConsoleEnabled(consoleEnabled);
        return SimpleResultUtils.createSimpleResult(resultVo);
    }
}
