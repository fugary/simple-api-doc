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

    public static final Integer STATUS_ENABLED = 1;

    public static final Integer STATUS_DISABLED = 0;

    public static final String API_USER_KEY = "simple-api-user";

    public static final String API_LOCALE_KEY = "locale";

    public static final String ALL_PATH_PATTERN = "/**";

    public static final String API_PATTERN = "/api";

    public static final String IMPORT_TYPE_FILE = "file";

    public static final String IMPORT_TYPE_URL = "url";

    public static final String FOLDER_PATH_SEPARATOR = "/";

    public static final String X_SIMPLE_FOLDER = "x-simple-folder";

    public static final String X_SIMPLE_MARKDOWN_FILES = "x-simple-markdown-files";

    public static final String X_APIFOX_FOLDER = "x-apifox-folder";

    public static final String AUTH_TYPE_BASIC = "Basic";

    public static final String DOC_TYPE_API = "api";

    public static final String DOC_TYPE_SCHEMA = "schema";

    public static final String DOC_TYPE_MD = "md";

    public static final String DOC_KEY_PREFIX = "x-simple-doc-";

    public static final String DOC_SCHEMA_TYPE_PARAMETERS = "parameters";

    public static final String DOC_SCHEMA_TYPE_REQUEST = "request";

    public static final String DOC_SCHEMA_TYPE_RESPONSE = "response";

    public static final String PROJECT_SCHEMA_TYPE_CONTENT = "content";

    public static final String PROJECT_SCHEMA_TYPE_COMPONENT = "component";

    public static final String PROJECT_SCHEMA_TYPE_SECURITY = "security";

    public static final String PROJECT_TASK_TYPE_MANUAL = "manual";

    public static final String PROJECT_TASK_TYPE_AUTO = "auto";

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
