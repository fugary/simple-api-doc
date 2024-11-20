package com.fugary.simple.api.web.vo;

import com.fugary.simple.api.web.vo.user.ApiUserVo;
import lombok.Data;

import java.io.Serializable;

@Data
public class LoginResultVo implements Serializable {

    private static final long serialVersionUID = -1911470282692277108L;
    private ApiUserVo account;
    private String accessToken;
    private boolean consoleEnabled;

}
