package com.fugary.simple.api.utils.exports;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.utils.SchemaJsonUtils;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.media.Schema;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Create date 2025/7/8<br>
 *
 * @author gary.fu
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiSchemaContentUtils {
    /**
     * x-default-auth 扩展字段 key
     */
    public static final String X_DEFAULT_AUTH = "x-default-auth";

    /**
     * 是否相同
     *
     * @param savedComponentSchemaContent
     * @param componentSchemaContent
     * @return
     */
    public static boolean isSameSchemaContent(String savedComponentSchemaContent, String componentSchemaContent) {
        String savedSchema = StringUtils.deleteWhitespace(savedComponentSchemaContent);
        String newSchema = StringUtils.deleteWhitespace(componentSchemaContent);
        return Objects.equals(savedSchema, newSchema);
    }

    private static final TypeReference<Map<String, Map<String, Object>>> SECURITY_SCHEMA_TYPE_REF =
            new TypeReference<Map<String, Map<String, Object>>>() {};

    /**
     * 合并 Security Schema Content，将已保存的 x-default-auth 字段保留到新的 schemaContent 中。
     * 导入文档时 OpenAPI 解析器不会保留自定义扩展字段，需在保存前合并回来。
     *
     * @param savedSecuritySchemaContent 已保存的 security schemaContent（含 x-default-auth）
     * @param newSecuritySchemaContent   新导入的 security schemaContent（不含 x-default-auth）
     * @return 合并后的 schemaContent
     */
    public static String mergeSecuritySchemaContent(String savedSecuritySchemaContent, String newSecuritySchemaContent) {
        if (StringUtils.isBlank(savedSecuritySchemaContent) || StringUtils.isBlank(newSecuritySchemaContent)) {
            return newSecuritySchemaContent;
        }
        try {
            Map<String, Map<String, Object>> savedMap = JsonUtils.fromJson(savedSecuritySchemaContent, SECURITY_SCHEMA_TYPE_REF);
            Map<String, Map<String, Object>> newMap = JsonUtils.fromJson(newSecuritySchemaContent, SECURITY_SCHEMA_TYPE_REF);
            if (savedMap == null || newMap == null) {
                return newSecuritySchemaContent;
            }
            // 将旧 schema 中的 x-default-auth 合并到新 schema 中
            savedMap.forEach((schemaName, savedSchema) -> {
                if (savedSchema != null && savedSchema.containsKey(X_DEFAULT_AUTH) && newMap.containsKey(schemaName)) {
                    newMap.get(schemaName).put(X_DEFAULT_AUTH, savedSchema.get(X_DEFAULT_AUTH));
                }
            });
            return JsonUtils.toJson(newMap);
        } catch (Exception e) {
            return newSecuritySchemaContent;
        }
    }

    /**
     * 合并SchemaContent数据
     *
     * @param savedComponentSchemaContent
     * @param componentSchemaContent
     * @param isV31
     * @return
     */
    public static String mergeComponentSchemaContent(String savedComponentSchemaContent, String componentSchemaContent, boolean isV31) {
        Schema<?> savedSchema = SchemaJsonUtils.fromJson(savedComponentSchemaContent, Schema.class, isV31);
        Schema<?> newSchema = SchemaJsonUtils.fromJson(componentSchemaContent, Schema.class, isV31);
        mergeSchema(savedSchema, newSchema);
        return SchemaJsonUtils.toJson(savedSchema, isV31);
    }

    /**
     * 保留旧Schema中的描述信息（用于未锁定的模型覆盖时）
     *
     * @param savedComponentSchemaContent
     * @param componentSchemaContent
     * @param isV31
     * @return
     */
    public static String retainComponentSchemaDescription(String savedComponentSchemaContent, String componentSchemaContent, boolean isV31) {
        Schema<?> savedSchema = SchemaJsonUtils.fromJson(savedComponentSchemaContent, Schema.class, isV31);
        Schema<?> newSchema = SchemaJsonUtils.fromJson(componentSchemaContent, Schema.class, isV31);
        retainSchemaDescription(savedSchema, newSchema);
        return SchemaJsonUtils.toJson(newSchema, isV31);
    }

    /**
     * 合并schema，优先长度长的数据
     *
     * @param savedSchema
     * @param newSchema
     */
    public static void mergeSchema(Schema<?> savedSchema, Schema<?> newSchema) {
        // properties
        if (savedSchema.getProperties() == null) {
            savedSchema.setProperties(newSchema.getProperties());
        } else if (newSchema.getProperties() != null) {
            for (Map.Entry<String, Schema> entry : newSchema.getProperties().entrySet()) {
                String propName = entry.getKey();
                Schema<?> newPropSchema = entry.getValue();
                Schema<?> savedPropSchema = (Schema<?>) savedSchema.getProperties().get(propName);

                if (savedPropSchema != null) {
                    // 合并属性
                    mergeStringField(savedPropSchema::getDescription, savedPropSchema::setDescription, newPropSchema.getDescription());
                    mergeStringField(savedPropSchema::getTitle, savedPropSchema::setTitle, newPropSchema.getTitle());
                    mergeStringField(savedPropSchema::getType, savedPropSchema::setType, newPropSchema.getType());

                    // example、default 覆盖式（如果新值不为空就覆盖）
                    if (newPropSchema.getExample() != null) {
                        savedPropSchema.setExample(newPropSchema.getExample());
                    }
                    if (newPropSchema.getDefault() != null) {
                        savedPropSchema.setDefault(newPropSchema.getDefault());
                    }

                    // externalDocs
                    mergeExternalDocs(savedPropSchema.getExternalDocs(), newPropSchema.getExternalDocs(), savedPropSchema);
                } else {
                    // 新增属性
                    savedSchema.getProperties().put(propName, newPropSchema);
                }
            }
        }

        // 顶层字段
        mergeStringField(savedSchema::getTitle, savedSchema::setTitle, newSchema.getTitle());
        mergeStringField(savedSchema::getDescription, savedSchema::setDescription, newSchema.getDescription());
        mergeStringField(savedSchema::getType, savedSchema::setType, newSchema.getType());

        if (newSchema.getExample() != null) {
            savedSchema.setExample(newSchema.getExample());
        }
        if (newSchema.getDefault() != null) {
            savedSchema.setDefault(newSchema.getDefault());
        }

        // required 合并去重
        mergeRequired(savedSchema, newSchema);

        // externalDocs
        mergeExternalDocs(savedSchema.getExternalDocs(), newSchema.getExternalDocs(), savedSchema);
    }

    /**
     * 仅保留/合并schema的描述相关字段（description, title, example, default），不合并属性结构
     *
     * @param savedSchema
     * @param newSchema
     */
    public static void retainSchemaDescription(Schema<?> savedSchema, Schema<?> newSchema) {
        if (savedSchema == null || newSchema == null) {
            return;
        }
        // properties
        if (savedSchema.getProperties() != null && newSchema.getProperties() != null) {
            for (Map.Entry<String, Schema> entry : newSchema.getProperties().entrySet()) {
                String propName = entry.getKey();
                Schema<?> newPropSchema = entry.getValue();
                Schema<?> savedPropSchema = (Schema<?>) savedSchema.getProperties().get(propName);

                if (savedPropSchema != null) {
                    mergeStringField(newPropSchema::getDescription, newPropSchema::setDescription, savedPropSchema.getDescription());
                    mergeStringField(newPropSchema::getTitle, newPropSchema::setTitle, savedPropSchema.getTitle());
                    if (newPropSchema.getExample() == null && savedPropSchema.getExample() != null) {
                        newPropSchema.setExample(savedPropSchema.getExample());
                    }
                    if (newPropSchema.getDefault() == null && savedPropSchema.getDefault() != null) {
                        newPropSchema.setDefault(savedPropSchema.getDefault());
                    }
                }
            }
        }
        // 顶层字段
        mergeStringField(newSchema::getDescription, newSchema::setDescription, savedSchema.getDescription());
        mergeStringField(newSchema::getTitle, newSchema::setTitle, savedSchema.getTitle());
        if (newSchema.getExample() == null && savedSchema.getExample() != null) {
            newSchema.setExample(savedSchema.getExample());
        }
        if (newSchema.getDefault() == null && savedSchema.getDefault() != null) {
            newSchema.setDefault(savedSchema.getDefault());
        }
    }

    private static void mergeRequired(Schema<?> savedSchema, Schema<?> newSchema) {
        Set<String> mergedRequired = new LinkedHashSet<>();
        if (savedSchema.getRequired() != null) {
            mergedRequired.addAll(savedSchema.getRequired());
        }
        if (newSchema.getRequired() != null) {
            mergedRequired.addAll(newSchema.getRequired());
        }
        if (!mergedRequired.isEmpty()) {
            savedSchema.setRequired(new ArrayList<>(mergedRequired));
        }
    }

    private static void mergeExternalDocs(ExternalDocumentation savedDocs, ExternalDocumentation newDocs, Schema<?> targetSchema) {
        if (savedDocs == null && newDocs != null) {
            targetSchema.setExternalDocs(newDocs);
        } else if (savedDocs != null && newDocs != null) {
            mergeStringField(savedDocs::getDescription, savedDocs::setDescription, newDocs.getDescription());
            mergeStringField(savedDocs::getUrl, savedDocs::setUrl, newDocs.getUrl());
        }
    }

    /**
     * 字符串字段 merge 策略：
     * - 如果 savedValue 为空，取 newValue
     * - 如果 newValue 不为空 且 长度 > savedValue，取 newValue
     * - 否则保留 savedValue
     */
    private static void mergeStringField(Supplier<String> getter, Consumer<String> setter, String newValue) {
        String savedValue = getter.get();
        if (savedValue == null) {
            setter.accept(newValue);
        } else if (newValue != null && newValue.length() > savedValue.length()) {
            setter.accept(newValue);
        }
    }
}
