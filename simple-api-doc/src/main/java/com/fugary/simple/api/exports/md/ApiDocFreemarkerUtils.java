package com.fugary.simple.api.exports.md;

import com.fugary.simple.api.utils.SimpleModelUtils;
import io.swagger.v3.core.util.RefUtils;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public class ApiDocFreemarkerUtils {
    /**
     * 解析Ref
     *
     * @param ref
     * @return
     */
    public String unRef(String ref) {
        if (StringUtils.isNotBlank(ref)) {
            return (String) RefUtils.extractSimpleName(ref).getKey();
        }
        return StringUtils.EMPTY;
    }

    /**
     * 计算类型
     *
     * @param schema
     * @return
     */
    public String propertyType(Schema<?> schema) {
        if (schema != null) {
            if (schema instanceof ArraySchema) {
                return "array<" + propertyType(schema.getItems()) + ">";
            }
            return StringUtils.defaultIfBlank(schema.getType(), unRef(schema.get$ref()));
        }
        return StringUtils.EMPTY;
    }

    /**
     * 属性是否是必填
     *
     * @param schema
     * @param property
     * @return
     */
    public boolean isRequired(Schema<?> schema, String property) {
        if (schema != null && schema.getRequired() != null) {
            return schema.getRequired().contains(property);
        }
        return false;
    }

    /**
     * 是否是true
     *
     * @param bool
     * @return
     */
    public boolean isTrue(Boolean bool) {
        return Boolean.TRUE.equals(bool);
    }

    /**
     * 获取Schema属性
     *
     * @param schema
     * @return
     */
    public Map<String, Schema> getSchemaProperties(Schema<?> schema) {
        if (schema != null) {
            if (MapUtils.isNotEmpty(schema.getProperties())) {
                return schema.getProperties();
            }
            if (schema.getItems() != null) {
                return getSchemaProperties(schema.getItems());
            }
            boolean xxxOf = CollectionUtils.isNotEmpty(schema.getAllOf())
                    || CollectionUtils.isNotEmpty(schema.getAnyOf())
                    || CollectionUtils.isNotEmpty(schema.getOneOf());
            if (xxxOf) {
                Map<String, Schema> properties = new LinkedHashMap<>();
                SimpleModelUtils.wrap(schema.getAllOf()).forEach(allOf -> properties.putAll(getSchemaProperties(allOf)));
                return properties;
            }
        }
        return new LinkedHashMap<>();
    }
}
