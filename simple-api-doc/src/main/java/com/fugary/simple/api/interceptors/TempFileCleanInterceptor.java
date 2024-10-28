package com.fugary.simple.api.interceptors;

import com.fugary.simple.api.contants.ApiDocConstants;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.function.Function;

/**
 * Create date 2024/10/28<br>
 *
 * @author gary.fu
 */
public class TempFileCleanInterceptor  implements HandlerInterceptor {

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        Object downloadHook = request.getAttribute(ApiDocConstants.SHARE_FILE_DOWNLOAD_HOOK_KEY);
        if (downloadHook instanceof Function) {
            Function<HttpServletRequest, Boolean> deleteFunc = (Function<HttpServletRequest, Boolean>) downloadHook;
            deleteFunc.apply(request);
        }
    }
}
