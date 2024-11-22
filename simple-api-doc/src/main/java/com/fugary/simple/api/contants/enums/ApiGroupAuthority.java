package com.fugary.simple.api.contants.enums;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

/**
 * Create date 2024/11/22<br>
 *
 * @author gary.fu
 */
@Getter
public enum ApiGroupAuthority {

    FORBIDDEN(0, "forbidden"),
    READABLE(1, "readable"),
    WRITABLE(2, "writable"),
    DELETABLE(3, "deletable");

    private final int level;
    private final String value;

    ApiGroupAuthority(int level, String value) {
        this.level = level;
        this.value = value;
    }

    public static ApiGroupAuthority fromValue(String value) {
        for (ApiGroupAuthority authority : ApiGroupAuthority.values()) {
            if (authority.getValue().equals(value)) {
                return authority;
            }
        }
        return null;
    }

    /**
     * 是否可以访问
     *
     * @param authorities
     * @param value
     * @return
     */
    public static boolean checkAccess(String authorities, String value) {
        ApiGroupAuthority authority = fromValue(value);
        return checkAccess(authorities, authority);
    }

    /**
     * 是否可以访问
     *
     * @param authorities
     * @param authority
     * @return
     */
    public static boolean checkAccess(String authorities, ApiGroupAuthority authority) {
        if (StringUtils.isBlank(authorities)) {
            return false;
        }
        if (authority == null) {
            return false;
        }
        String[] values = authorities.split("\\s*,\\s*");
        for (String val : values) {
            ApiGroupAuthority groupAuth = fromValue(val);
            if (groupAuth != null && groupAuth.getLevel() >= authority.getLevel()) {
                return true;
            }
        }
        return false;
    }
}
