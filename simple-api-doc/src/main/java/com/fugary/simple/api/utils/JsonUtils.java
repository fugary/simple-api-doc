package com.fugary.simple.api.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Created on 2020/5/14 18:31 .<br>
 *
 * @author gary.fu
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class JsonUtils {
    /**
     * 默认Mapper
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.disable(SerializationFeature.INDENT_OUTPUT); // 去掉缩进
        MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        MAPPER.enable(MapperFeature.USE_WRAPPER_NAME_AS_PROPERTY_NAME);
        MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL); // 不解析null的值
        MAPPER.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        MAPPER.disable(MapperFeature.USE_WRAPPER_NAME_AS_PROPERTY_NAME);
        // 注册JavaTimeModule
        JavaTimeModule module = new JavaTimeModule();
        MAPPER.registerModule(module);
    }

    public static ObjectMapper getMapper(){
        return MAPPER;
    }

    /**
     * 输出到Json字符串
     *
     * @param input
     * @return
     */
    public static String toJson(Object input) {
        String result = StringUtils.EMPTY;
        try (ByteArrayOutputStream bio = new ByteArrayOutputStream();
             JsonGenerator jsonGenerator = MAPPER.getFactory().createGenerator(bio)) {
            jsonGenerator.writeObject(input);
            result = bio.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            log.error("输出Json错误", e);
        }
        return result;
    }

    /**
     * json字符串转对象
     *
     * @param json
     * @param clazz
     * @return
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        T result = null;
        try {
            result = MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            log.error("解析Json错误", e);
        }
        return result;
    }

    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        T result = null;
        try {
            if (StringUtils.isNotBlank(json)) {
                result = MAPPER.readValue(json, typeReference);
            }
        } catch (IOException e) {
            log.error("将Json转换成对象出错", e);
        }

        return result;
    }

    /**
     * json数据判断
     *
     * @param template
     * @return
     */
    public static boolean isJson(String template) {
        template = StringUtils.trimToEmpty(template);
        return StringUtils.isNotBlank(template)
                && StringUtils.startsWithAny(template, "{", "[")
                && StringUtils.endsWithAny(template, "}", "]");
    }

}
