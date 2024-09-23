package com.fugary.simple.api.contants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Created on 2020/5/4 9:19 .<br>
 *
 * @author gary.fu
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiDocConstants {

    public static final String API_USER_KEY = "simple-api-user";

    public static final String API_LOCALE_KEY = "locale";

    public static final String ALL_PATH_PATTERN = "/**";

    public static final String API_PATTERN = "/api";

    public static final String IMPORT_TYPE_FILE = "file";

    public static final String IMPORT_TYPE_URL = "url";

    /**
     * 中止导入
     */
    public static final Integer IMPORT_STRATEGY_ERROR = 1;
    /**
     * 跳过重复的
     */
    public static final Integer IMPORT_STRATEGY_SKIP = 2;
    /**
     * 生成新的
     */
    public static final Integer IMPORT_STRATEGY_NEW = 3;

}
