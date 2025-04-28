package com.fugary.simple.api.contants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Created on 2020/5/4 9:19 .<br>
 *
 * @author gary.fu
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiDocConstants {

    public static final Integer STATUS_ENABLED = 1;

    public static final Integer STATUS_DISABLED = 0;

    public static final String STATUS_KEY = "status";

    public static final String CREATOR_KEY = "creator";

    public static final String CREATE_DATE_KEY = "createDate";

    public static final String MODIFIER_KEY = "modifier";

    public static final String MODIFY_DATE_KEY = "modifyDate";

    public static final String API_USER_KEY = "simple-api-user";

    public static final String AUTHORIZED_SHARED_KEY = "simple-api-share-id";

    public static final String SIMPLE_API_TARGET_URL_HEADER = "simple-api-target-url";

    public static final String SIMPLE_API_DEBUG_HEADER = "simple-api-debug";

    public static final String SIMPLE_API_META_DATA_REQ = "simple-api-meta-req";

    public static final String SIMPLE_API_ACCESS_TOKEN_HEADER = "simple-api-access-token";

    public static final String API_LOCALE_KEY = "locale";

    public static final String ALL_PATH_PATTERN = "/**";

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

    public static final String AUTO_IMPORT_TASK_PREFIX = "auto-import-";

    public static final String DOC_SCHEMA_TYPE_PARAMETERS = "parameters";

    public static final String DOC_SCHEMA_TYPE_REQUEST = "request";

    public static final String DOC_SCHEMA_TYPE_RESPONSE = "response";

    public static final String PROJECT_SCHEMA_TYPE_CONTENT = "content";

    public static final String PROJECT_SCHEMA_TYPE_COMPONENT = "component";

    public static final String PROJECT_SCHEMA_TYPE_SECURITY = "security";

    public static final String SCHEMA_TYPE_SECURITY_REQUIREMENT = "security_requirements";

    public static final String PROJECT_TASK_TYPE_MANUAL = "manual";

    public static final String PROJECT_TASK_TYPE_AUTO = "auto";

    public static final String SIMPLE_TASK_TYPE_FIXED = "FixedRate";

    public static final String SIMPLE_TASK_TYPE_CRON = "Cron";

    public static final String TASK_STATUS_STARTED = "started";

    public static final String TASK_STATUS_DONE = "done";

    public static final String TASK_STATUS_ERROR = "error";

    public static final String TASK_STATUS_RUNNING = "running";

    public static final String TASK_STATUS_STOPPED = "stopped";

    public static final String SCHEMA_COMPONENT_PREFIX = "#/components/schemas/";

    public static final String SECURITY_CONFUSION_VALUE = "***";

    public static final String SHARE_FILE_DOWNLOAD_HOOK_KEY = "share-file-download-hook";

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
    /**
     * 常用schemaTypes
     */
    public static final Set<String> PROJECT_SCHEMA_TYPES = Set.of(PROJECT_SCHEMA_TYPE_COMPONENT, PROJECT_SCHEMA_TYPE_SECURITY, SCHEMA_TYPE_SECURITY_REQUIREMENT);

    /**
     * copy的后缀
     */
    public static final String COPY_SUFFIX = "-copy";

    /**
     * 成功
     */
    public static final String SUCCESS = "SUCCESS";

    /**
     * 失败
     */
    public static final String FAIL = "FAIL";
}
