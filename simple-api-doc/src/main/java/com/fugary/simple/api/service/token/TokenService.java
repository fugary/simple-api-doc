package com.fugary.simple.api.service.token;

import com.fugary.simple.api.entity.api.ApiUser;
import com.fugary.simple.api.web.vo.SimpleResult;

/**
 * Created on 2020/5/5 20:41 .<br>
 *
 * @author gary.fu
 */
public interface TokenService {

    /**
     * 生成Token
     *
     * @param user
     * @return
     */
    String createToken(ApiUser user);

    /**
     * 获取用户
     * @param accessToken
     * @return
     */
    ApiUser fromAccessToken(String accessToken);

    /**
     * 验证
     * @param accessToken
     * @return
     */
    SimpleResult<ApiUser> validate(String accessToken);
}
