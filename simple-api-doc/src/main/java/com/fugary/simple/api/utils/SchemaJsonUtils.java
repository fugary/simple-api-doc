package com.fugary.simple.api.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.SpecVersion;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;

/**
 * Create date 2024/10/23<br>
 *
 * @author gary.fu
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class SchemaJsonUtils {

    /**
     * 默认Mapper
     */
    private static final ObjectMapper MAPPER = Json.mapper();

    /**
     * 默认Mapper
     */
    private static final ObjectMapper MAPPER_V31 = Json31.mapper();

    public static ObjectMapper getMapper(boolean v31) {
        return v31 ? MAPPER_V31 : MAPPER;
    }

    /**
     * 对象转yaml
     *
     * @param input
     * @return
     */
    public static String toJson(Object input, boolean v31) {
        String result = StringUtils.EMPTY;
        try {
            result = getMapper(v31).writeValueAsString(input);
        } catch (Exception e) {
            log.error("输出json错误", e);
        }
        return result;
    }

    /**
     * yaml转对象
     *
     * @param input
     * @param clazz
     * @param v31
     * @param <T>
     * @return
     */
    public static <T> T fromJson(String input, Class<T> clazz, boolean v31) {
        T result = null;
        try {
            result = getMapper(v31).readValue(input, clazz);
        } catch (Exception e) {
            log.error("输出json错误", e);
        }
        return result;
    }


    public static <T> T fromJson(String json, TypeReference<T> typeReference, boolean v31) {
        T result = null;
        try {
            if (StringUtils.isNotBlank(json)) {
                result = getMapper(v31).readValue(json, typeReference);
            }
        } catch (IOException e) {
            log.error("将Json转换成对象出错", e);
        }

        return result;
    }

    public static boolean isV31(OpenAPI openAPI) {
        return SpecVersion.V31.equals(openAPI.getSpecVersion());
    }
}
