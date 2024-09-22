package com.fugary.simple.api.web.controllers;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.ApiUser;
import com.fugary.simple.api.service.apidoc.ApiUserService;
import com.fugary.simple.api.service.token.TokenService;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.LoginResultVo;
import com.fugary.simple.api.web.vo.SimpleResult;
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
    public SimpleResult<LoginResultVo> login(@RequestBody ApiUser user) {
        ApiUser loginUser = apiUserService.getOne(Wrappers.<ApiUser>query().eq("user_name",
                user.getUserName()));
        if (loginUser == null || !apiUserService.matchPassword(user.getUserPassword(), loginUser.getUserPassword())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2001);
        }
        LoginResultVo resultVo = new LoginResultVo();
        resultVo.setAccount(loginUser);
        resultVo.setAccessToken(tokenService.createToken(loginUser));
        resultVo.setConsoleEnabled(consoleEnabled);
        return SimpleResultUtils.createSimpleResult(resultVo);
    }
}
