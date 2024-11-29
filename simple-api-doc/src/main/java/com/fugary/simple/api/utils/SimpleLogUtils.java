package com.fugary.simple.api.utils;

import com.fugary.simple.api.entity.api.ApiLog;
import com.fugary.simple.api.web.vo.query.ApiParamsVo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Create date 2024/11/27<br>
 *
 * @author gary.fu
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SimpleLogUtils {

    private static final ThreadLocal<ApiLog.ApiLogBuilder> LOG_BUILDER_HOLDER = new ThreadLocal<>();

    public static void setLogBuilder(ApiLog.ApiLogBuilder logBuilder) {
        LOG_BUILDER_HOLDER.set(logBuilder);
    }

    public static ApiLog.ApiLogBuilder getLogBuilder() {
        return LOG_BUILDER_HOLDER.get();
    }

    public static void clearLogBuilder() {
        LOG_BUILDER_HOLDER.remove();
    }

    /**
     * 添加日志数据
     *
     * @param consumer
     */
    public static void addLogData(Consumer<ApiLog.ApiLogBuilder> consumer) {
        ApiLog.ApiLogBuilder logBuilder = getLogBuilder();
        if (logBuilder != null) {
            consumer.accept(logBuilder);
        }
    }

    /**
     * 添加responseBody数据
     *
     * @param responseBody
     */
    public static void addResponseData(String responseBody) {
        addLogData(logBuilder -> logBuilder.responseBody(responseBody));
    }

    /**
     * 添加responseBody数据
     *
     * @param responseEntity
     */
    public static void addResponseData(ResponseEntity<?> responseEntity) {
        String responseBody = getResponseBody(responseEntity);
        if (StringUtils.isNotBlank(responseBody)) {
            addResponseData(responseBody);
        }
    }

    public static String getResponseBody(ResponseEntity<?> responseEntity) {
        if (responseEntity != null && responseEntity.getBody() != null) {
            if(responseEntity.getBody() instanceof String){
                return (String) responseEntity.getBody();
            }
            if(responseEntity.getBody() instanceof byte[]){
                return new String((byte[]) responseEntity.getBody(), StandardCharsets.UTF_8);
            }
            return JsonUtils.toJson(responseEntity.getBody());
        }
        return null;
    }

    /**
     * 添加request数据
     *
     * @param requestData
     */
    public static void addRequestData(String requestData) {
        addLogData(logBuilder -> logBuilder.logData(requestData));
    }

    /**
     * 添加request数据
     *
     * @param apiParams
     */
    public static void addRequestData(ApiParamsVo apiParams) {
        if (apiParams != null) {
            String requestData = apiParams.getRequestBody();
            if(StringUtils.isBlank(requestData) && !apiParams.getRequestParams().isEmpty()){
                requestData = JsonUtils.toJson(apiParams.getRequestParams());
            }
            addRequestData(requestData);
        }
    }
}
