package com.fugary.simple.api.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.core.util.RefUtils;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.Schema;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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

    /**
     * 最简单mapper
     */
    public static final ObjectMapper FORMATED_MAPPER = new ObjectMapper();

    static {
        FORMATED_MAPPER.enable(SerializationFeature.INDENT_OUTPUT); // 开启缩进
    }

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

    public static boolean isV31(SpecVersion specVersion) {
        return SpecVersion.V31.equals(specVersion);
    }

    public static void processXxxOf(List<Schema> xxxOf, Map<String, Schema<?>> schemasMap) {
        xxxOf = SimpleModelUtils.wrap(xxxOf);
        for (int i = 0; i < xxxOf.size(); i++) {
            Schema<?> calcSchema = getSchema(xxxOf.get(i), schemasMap);
            if (calcSchema != null) {
                xxxOf.set(i, calcSchema);
            }
        }
    }

    public static Schema<?> getSchema(Schema<?> schema, Map<String, Schema<?>> schemasMap) {
        if (schema != null) {
            if (StringUtils.isNotBlank(schema.get$ref())) {
                String refType = (String) RefUtils.extractSimpleName(schema.get$ref()).getKey();
                schema = schemasMap.get(refType);
                if (schema != null && StringUtils.isBlank(schema.getName())) {
                    schema.setName(refType);
                }
            }
            if (schema != null) {
                boolean xxxOf = CollectionUtils.isNotEmpty(schema.getAllOf())
                        || CollectionUtils.isNotEmpty(schema.getAnyOf())
                        || CollectionUtils.isNotEmpty(schema.getOneOf());
                if (xxxOf) {
                    processXxxOf(schema.getAllOf(), schemasMap);
                    processXxxOf(schema.getAnyOf(), schemasMap);
                    processXxxOf(schema.getOneOf(), schemasMap);
                }
            }
        }
        return schema;
    }

    /**
     * 自定义Mapper
     * @param mapper
     * @param input
     * @return
     */
    public static String toJson(ObjectMapper mapper, Object input) {
        String result = StringUtils.EMPTY;
        try {
            result = mapper.writeValueAsString(input);
        } catch (Exception e) {
            log.error("输出json错误", e);
        }
        return result;
    }
}
