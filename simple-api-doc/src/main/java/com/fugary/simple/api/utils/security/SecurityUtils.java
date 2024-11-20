package com.fugary.simple.api.utils.security;

import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.ApiProjectShare;
import com.fugary.simple.api.entity.api.ApiUser;
import com.fugary.simple.api.utils.servlet.HttpRequestUtils;
import com.fugary.simple.api.web.vo.user.ApiUserVo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;

/**
 * Create date 2024/7/5<br>
 *
 * @author gary.fu
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SecurityUtils {

    public static final String ADMIN_USER = "admin";

    /**
     * 获取登录用户
     *
     * @return
     */
    public static ApiUserVo getLoginUser() {
        HttpServletRequest request = HttpRequestUtils.getCurrentRequest();
        if (request != null) {
            return (ApiUserVo) request.getAttribute(ApiDocConstants.API_USER_KEY);
        }
        return null;
    }

    /**
     * 获取登录用户名
     * @return
     */
    public static String getLoginUserName() {
        ApiUser loginUser = getLoginUser();
        return loginUser != null ? loginUser.getUserName() : "";
    }

    /**
     * 获取ShareId
     * @return
     */
    public static String getLoginShareId() {
        HttpServletRequest request = HttpRequestUtils.getCurrentRequest();
        if (request != null) {
            return (String) request.getAttribute(ApiDocConstants.AUTHORIZED_SHARED_KEY);
        }
        return null;
    }

    /**
     * 获取分享shareId组合信息
     * @return
     */
    public static String getShareUserName(ApiProjectShare share) {
        StringBuilder sb = new StringBuilder(share.getShareId());
        if (StringUtils.isNotBlank(share.getSharePassword())) {
            sb.append(":").append(DigestUtils.sha256Hex(share.getSharePassword()));
        }
        return sb.toString();
    }

    /**
     * 获取分享的shareId
     * @return
     */
    public static String getUserShareId(ApiUser apiUser) {
        String userName = apiUser.getUserName();
        int index = StringUtils.indexOf(userName, ":");
        if (index > -1) {
            return userName.substring(0, index);
        }
        return userName;
    }

    /**
     * 验证分享
     *
     * @return
     */
    public static boolean validateShareUserName(String userName, ApiProjectShare share) {
        if (share != null) {
            if (StringUtils.isBlank(share.getSharePassword())) {
                return true;
            }
            int index = StringUtils.indexOf(userName, ":");
            String shareId = userName;
            String password = "";
            if (index > -1) {
                shareId = userName.substring(0, index);
                password = userName.substring(index + 1);
            }
            return StringUtils.equals(shareId, share.getShareId())
                    && StringUtils.equals(password, DigestUtils.sha256Hex(share.getSharePassword()));
        }
        return false;
    }

    /**
     * 验证用户操作
     *
     * @param targetUserName
     * @return
     */
    public static boolean validateUserUpdate(String targetUserName) {
        ApiUser loginUser = getLoginUser();
        if (loginUser != null && StringUtils.isNotBlank(targetUserName)) {
            return ADMIN_USER.equals(loginUser.getUserName()) || loginUser.getUserName().equals(targetUserName);
        }
        return false;
    }

    /**
     * 判断是否是admin
     * @return
     */
    public static boolean isAdmin(){
        ApiUser loginUser = getLoginUser();
        if (loginUser != null) {
            return ADMIN_USER.equals(loginUser.getUserName());
        }
        return false;
    }

    /**
     * 获取可用的用户名
     *
     * @param queryUserName
     * @return
     */
    public static String getUserName(String queryUserName) {
        ApiUser loginUser = getLoginUser();
        String userName = StringUtils.defaultIfBlank(queryUserName, loginUser != null ? loginUser.getUserName() : "");
        userName = SecurityUtils.validateUserUpdate(userName) ? userName : "";
        return userName;
    }
}
