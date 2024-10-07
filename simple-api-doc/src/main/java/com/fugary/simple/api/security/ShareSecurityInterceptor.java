package com.fugary.simple.api.security;

import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.ApiUser;
import com.fugary.simple.api.service.token.TokenService;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * 使用简单拦截器控制链接安全<br>
 * Create date 2024/10/02<br>
 *
 * @author gary.fu
 */
@Getter
@Setter
public class ShareSecurityInterceptor implements HandlerInterceptor {

    @Autowired
    private TokenService tokenService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String authorization = StringUtils.defaultIfBlank(request.getHeader(ApiDocConstants.SIMPLE_API_ACCESS_TOKEN_HEADER), request.getHeader(HttpHeaders.AUTHORIZATION));
        SimpleResult<ApiUser> userResult = null;
        if (StringUtils.isNotBlank(authorization)) {
            String accessToken = authorization.replaceFirst("Bearer ", StringUtils.EMPTY).trim();
            userResult = getTokenService().validateTokenOnly(accessToken);
            if (userResult.isSuccess()) {
                request.setAttribute(ApiDocConstants.AUTHORIZED_SHARED_KEY, userResult.getResultData().getUserName());
                return true;
            }
        }
        responseJson(response, userResult == null ? SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_401) : userResult);
        return false;
    }

    protected void responseJson(HttpServletResponse response, SimpleResult<ApiUser> userResult) throws IOException {
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try (PrintWriter out = response.getWriter()) {
            out.write(JsonUtils.toJson(userResult));
            out.flush();
        }
    }

}
