package com.fugary.simple.api.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.util.Json;
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

    public static ObjectMapper getMapper() {
        return MAPPER;
    }

    /**
     * 对象转yaml
     *
     * @param input
     * @return
     */
    public static String toJson(Object input) {
        String result = StringUtils.EMPTY;
        try {
            result = getMapper().writeValueAsString(input);
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
     * @param <T>
     * @return
     */
    public static <T> T fromJson(String input, Class<T> clazz) {
        T result = null;
        try {
            result = getMapper().readValue(input, clazz);
        } catch (Exception e) {
            log.error("输出json错误", e);
        }
        return result;
    }


    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        T result = null;
        try {
            if (StringUtils.isNotBlank(json)) {
                result = getMapper().readValue(json, typeReference);
            }
        } catch (IOException e) {
            log.error("将Json转换成对象出错", e);
        }

        return result;
    }
}
