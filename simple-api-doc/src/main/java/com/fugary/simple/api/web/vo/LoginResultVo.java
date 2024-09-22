package com.fugary.simple.api.web.vo;

import com.fugary.simple.api.entity.api.ApiUser;
import lombok.Data;

import java.io.Serializable;

@Data
public class LoginResultVo implements Serializable {

    private static final long serialVersionUID = -1911470282692277108L;
    private ApiUser account;
    private String accessToken;
    private boolean consoleEnabled;

}
