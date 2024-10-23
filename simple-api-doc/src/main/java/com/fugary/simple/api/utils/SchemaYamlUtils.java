package com.fugary.simple.api.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.util.Yaml;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Create date 2024/7/24<br>
 *
 * @author gary.fu
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SchemaYamlUtils {

    /**
     * 默认Mapper
     */
    private static final ObjectMapper MAPPER = Yaml.mapper();

    public static ObjectMapper getMapper() {
        return MAPPER;
    }

    /**
     * 对象转yaml
     *
     * @param input
     * @return
     */
    public static String toYaml(Object input) {
        String result = StringUtils.EMPTY;
        try {
            result = getMapper().writeValueAsString(input);
        } catch (Exception e) {
            log.error("输出yaml错误", e);
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
    public static <T> T fromYaml(String input, Class<T> clazz) {
        T result = null;
        try {
            result = getMapper().readValue(input, clazz);
        } catch (Exception e) {
            log.error("输出yaml错误", e);
        }
        return result;
    }
}
